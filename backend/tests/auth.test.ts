import { describe, it, expect, vi, beforeEach } from 'vitest';
import {
  hashPassword,
  verifyPassword,
  generateAccessToken,
  generateRefreshToken,
  verifyAccessToken,
  verifyRefreshToken,
  saveRefreshToken,
  rotateRefreshToken,
  generateOtpCode,
  createPasswordResetOtp,
  verifyAndConsumeOtp,
} from '@/lib/auth';
import { prisma } from '@/lib/prisma';

vi.mock('@/lib/prisma', () => ({
  prisma: {
    refreshToken: {
      create: vi.fn(),
      findUnique: vi.fn(),
      update: vi.fn(),
      deleteMany: vi.fn(),
    },
    passwordResetOtp: {
      updateMany: vi.fn(),
      create: vi.fn(),
      findMany: vi.fn(),
      update: vi.fn(),
    },
  },
}));

describe('Auth Service - Segurança e Gestão de Sessões', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('Criptografia e Validação de Senhas', () => {
    it('deve gerar um hash bcrypt válido diferente do texto plano', async () => {
      const password = 'MinhaSenhaSegura@2026';
      const hash = await hashPassword(password);

      expect(hash).toBeDefined();
      expect(hash).not.toBe(password);
      expect(hash.startsWith('$2')).toBe(true);
    });

    it('deve verificar senha correta com sucesso (true)', async () => {
      const password = 'MinhaSenhaSegura@2026';
      const hash = await hashPassword(password);
      const isValid = await verifyPassword(password, hash);

      expect(isValid).toBe(true);
    });

    it('deve rejeitar senha incorreta (false)', async () => {
      const password = 'MinhaSenhaSegura@2026';
      const hash = await hashPassword(password);
      const isValid = await verifyPassword('SenhaErrada#123', hash);

      expect(isValid).toBe(false);
    });
  });

  describe('Tokens JWT de Acesso e Atualização', () => {
    const payload = { userId: 'user-uuid-1234', email: 'chef@gusto.app' };

    it('deve gerar e decodificar token de acesso com o payload correto', () => {
      const token = generateAccessToken(payload);
      expect(typeof token).toBe('string');

      const decoded = verifyAccessToken(token);
      expect(decoded).not.toBeNull();
      expect(decoded?.userId).toBe(payload.userId);
      expect(decoded?.email).toBe(payload.email);
    });

    it('deve gerar e decodificar token de refresh válido', () => {
      const token = generateRefreshToken(payload);
      expect(typeof token).toBe('string');

      const decoded = verifyRefreshToken(token);
      expect(decoded).not.toBeNull();
      expect(decoded?.userId).toBe(payload.userId);
      expect(decoded?.email).toBe(payload.email);
    });

    it('deve retornar null ao verificar token de acesso forjado ou inválido', () => {
      const result = verifyAccessToken('token-totalmente-invalido.123.456');
      expect(result).toBeNull();
    });

    it('deve retornar null ao verificar refresh token forjado', () => {
      const result = verifyRefreshToken('refresh-token-invalido');
      expect(result).toBeNull();
    });
  });

  describe('Persistência e Rotação de Refresh Tokens', () => {
    const payload = { userId: 'user-uuid-1234', email: 'chef@gusto.app' };

    it('deve persistir um novo refresh token no banco de dados', async () => {
      await saveRefreshToken(payload.userId, 'refresh-token-exemplo');

      expect(prisma.refreshToken.create).toHaveBeenCalledTimes(1);
      expect(prisma.refreshToken.create).toHaveBeenCalledWith(
        expect.objectContaining({
          data: expect.objectContaining({
            userId: payload.userId,
            token: 'refresh-token-exemplo',
          }),
        })
      );
    });

    it('deve rotacionar refresh token ativo revogando o antigo e emitindo novo par', async () => {
      const oldToken = generateRefreshToken(payload);

      vi.mocked(prisma.refreshToken.findUnique).mockResolvedValue({
        id: 'token-db-id',
        token: oldToken,
        userId: payload.userId,
        revoked: false,
        expiresAt: new Date(Date.now() + 1000 * 60 * 60 * 24),
        createdAt: new Date(),
        updatedAt: new Date(),
      } as any);

      const result = await rotateRefreshToken(oldToken);

      expect(result).not.toBeNull();
      expect(result?.userId).toBe(payload.userId);
      expect(result?.accessToken).toBeDefined();
      expect(result?.refreshToken).toBeDefined();

      // Verifica se o token antigo foi revogado
      expect(prisma.refreshToken.update).toHaveBeenCalledWith({
        where: { id: 'token-db-id' },
        data: { revoked: true },
      });
      // Verifica persistência do novo token
      expect(prisma.refreshToken.create).toHaveBeenCalled();
    });

    it('deve detectar ataque de reuso de token revogado, purgar sessões do usuário e retornar null', async () => {
      const compromisedToken = generateRefreshToken(payload);

      vi.mocked(prisma.refreshToken.findUnique).mockResolvedValue({
        id: 'token-revoked-id',
        token: compromisedToken,
        userId: payload.userId,
        revoked: true, // Já revogado previamente!
        expiresAt: new Date(Date.now() + 1000 * 60 * 60),
        createdAt: new Date(),
        updatedAt: new Date(),
      } as any);

      const result = await rotateRefreshToken(compromisedToken);

      expect(result).toBeNull();
      // Purga de segurança de todas as sessões do usuário comprometido
      expect(prisma.refreshToken.deleteMany).toHaveBeenCalledWith({
        where: { userId: payload.userId },
      });
    });

    it('deve retornar null se o token não existir no banco', async () => {
      const validToken = generateRefreshToken(payload);
      vi.mocked(prisma.refreshToken.findUnique).mockResolvedValue(null);

      const result = await rotateRefreshToken(validToken);
      expect(result).toBeNull();
    });
  });

  describe('Geração e Validação de Código OTP de Recuperação de Senha', () => {
    it('deve gerar código OTP numérico exatamente com 6 dígitos', () => {
      const otp = generateOtpCode();
      expect(otp).toMatch(/^\d{6}$/);
      expect(otp.length).toBe(6);
    });

    it('deve invalidar OTPs pendentes anteriores e criar novo hash com 10 min de validade', async () => {
      const email = 'recuperacao@gusto.app';
      const userId = 'user-otp-999';

      const rawOtp = await createPasswordResetOtp(userId, email);

      expect(rawOtp).toMatch(/^\d{6}$/);
      // Invalidação prévia
      expect(prisma.passwordResetOtp.updateMany).toHaveBeenCalledWith({
        where: { email, consumed: false },
        data: { consumed: true },
      });
      // Criação do novo registro
      expect(prisma.passwordResetOtp.create).toHaveBeenCalledWith(
        expect.objectContaining({
          data: expect.objectContaining({
            userId,
            email,
          }),
        })
      );
    });

    it('deve validar e consumir OTP com código correto', async () => {
      const email = 'recuperacao@gusto.app';
      const correctCode = '654321';
      const hashedCode = await hashPassword(correctCode);

      vi.mocked(prisma.passwordResetOtp.findMany).mockResolvedValue([
        {
          id: 'otp-id-1',
          userId: 'user-recuperado-123',
          email,
          codeHash: hashedCode,
          consumed: false,
          expiresAt: new Date(Date.now() + 500000),
          createdAt: new Date(),
        } as any,
      ]);

      const userId = await verifyAndConsumeOtp(email, correctCode);

      expect(userId).toBe('user-recuperado-123');
      expect(prisma.passwordResetOtp.update).toHaveBeenCalledWith({
        where: { id: 'otp-id-1' },
        data: { consumed: true },
      });
    });

    it('deve rejeitar e retornar null para OTP incorreto', async () => {
      const email = 'recuperacao@gusto.app';
      const hashedCode = await hashPassword('999999');

      vi.mocked(prisma.passwordResetOtp.findMany).mockResolvedValue([
        {
          id: 'otp-id-1',
          userId: 'user-123',
          email,
          codeHash: hashedCode,
          consumed: false,
          expiresAt: new Date(Date.now() + 500000),
          createdAt: new Date(),
        } as any,
      ]);

      const result = await verifyAndConsumeOtp(email, '000000');
      expect(result).toBeNull();
      expect(prisma.passwordResetOtp.update).not.toHaveBeenCalled();
    });
  });
});
