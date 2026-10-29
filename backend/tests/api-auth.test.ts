import { describe, it, expect, vi, beforeEach } from 'vitest';
import { POST as registerHandler } from '@/app/api/auth/register/route';
import { POST as loginHandler } from '@/app/api/auth/login/route';
import { POST as refreshHandler } from '@/app/api/auth/refresh/route';
import { POST as forgotPasswordHandler } from '@/app/api/auth/forgot-password/route';
import { POST as resetPasswordHandler } from '@/app/api/auth/reset-password/route';
import { prisma } from '@/lib/prisma';
import { hashPassword, rotateRefreshToken, createPasswordResetOtp, verifyAndConsumeOtp } from '@/lib/auth';

vi.mock('@/lib/prisma', () => ({
  prisma: {
    user: {
      findUnique: vi.fn(),
      create: vi.fn(),
      update: vi.fn(),
    },
    refreshToken: {
      create: vi.fn(),
      updateMany: vi.fn(),
    },
    $transaction: vi.fn().mockImplementation((callbacks) => Promise.all(callbacks)),
  },
}));

vi.mock('@/lib/email', () => ({
  sendPasswordResetEmail: vi.fn().mockResolvedValue({ success: true }),
}));

vi.mock('@/lib/auth', async () => {
  const actual = await vi.importActual<any>('@/lib/auth');
  return {
    ...actual,
    rotateRefreshToken: vi.fn(),
    createPasswordResetOtp: vi.fn(),
    verifyAndConsumeOtp: vi.fn(),
  };
});

function createJsonRequest(url: string, body: any, ip: string = '192.168.1.10', cookieToken?: string): any {
  return {
    url,
    cookies: {
      get: (name: string) => (name === 'refreshToken' && cookieToken ? { value: cookieToken } : undefined),
    },
    headers: {
      get: (header: string) => {
        if (header.toLowerCase() === 'x-forwarded-for') return ip;
        if (header.toLowerCase() === 'content-type') return 'application/json';
        return null;
      },
    },
    json: async () => body,
  };
}

describe('API de Autenticação - Rotas e Contratos HTTP', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('POST /api/auth/register', () => {
    it('deve rejeitar cadastro com payload incompleto ou senha fraca (400)', async () => {
      const invalidPayload = {
        name: 'A', // menor que 2 caracteres
        email: 'email-invalido',
        password: 'fraca', // sem maiúscula, sem número, menor que 8
      };

      const req = createJsonRequest('http://localhost:3000/api/auth/register', invalidPayload, '10.10.1.1');
      const res = await registerHandler(req);
      const data = await res.json();

      expect(res.status).toBe(400);
      expect(data.error).toBe('Dados inválidos');
      expect(data.details).toBeDefined();
    });

    it('deve rejeitar cadastro se e-mail já estiver em uso (409 Conflict)', async () => {
      vi.mocked(prisma.user.findUnique).mockResolvedValue({
        id: 'existing-id',
        email: 'chef@gusto.app',
      } as any);

      const validPayload = {
        name: 'Chef Gusto',
        email: 'chef@gusto.app',
        password: 'SenhaForte@2026',
      };

      const req = createJsonRequest('http://localhost:3000/api/auth/register', validPayload, '10.10.1.2');
      const res = await registerHandler(req);
      const data = await res.json();

      expect(res.status).toBe(409);
      expect(data.error).toContain('já existe');
    });

    it('deve registrar usuário com sucesso, gerar tokens e persistir hash (201)', async () => {
      vi.mocked(prisma.user.findUnique).mockResolvedValue(null);
      vi.mocked(prisma.user.create).mockResolvedValue({
        id: 'new-user-uuid',
        name: 'Chef Gusto',
        email: 'chef@gusto.app',
        themePreference: 'OLED',
        createdAt: new Date(),
      } as any);

      const validPayload = {
        name: 'Chef Gusto',
        email: 'chef@gusto.app',
        password: 'SenhaForte@2026',
      };

      const req = createJsonRequest('http://localhost:3000/api/auth/register', validPayload, '10.10.1.3');
      const res = await registerHandler(req);
      const data = await res.json();

      expect(res.status).toBe(201);
      expect(data.message).toContain('sucesso');
      expect(data.accessToken).toBeDefined();
      expect(data.refreshToken).toBeDefined();
      expect(data.user.id).toBe('new-user-uuid');
      expect(prisma.refreshToken.create).toHaveBeenCalled();
    });
  });

  describe('POST /api/auth/login', () => {
    it('deve rejeitar login com credenciais inexistentes (401)', async () => {
      vi.mocked(prisma.user.findUnique).mockResolvedValue(null);

      const req = createJsonRequest('http://localhost:3000/api/auth/login', {
        email: 'naoexiste@gusto.app',
        password: 'SenhaQualquer1',
      }, '10.10.2.1');

      const res = await loginHandler(req);
      const data = await res.json();

      expect(res.status).toBe(401);
      expect(data.error).toBe('Credenciais inválidas');
    });

    it('deve rejeitar login quando a senha estiver incorreta (401)', async () => {
      const realPasswordHash = await hashPassword('SenhaCorreta@2026');
      vi.mocked(prisma.user.findUnique).mockResolvedValue({
        id: 'user-login-id',
        email: 'usuario@gusto.app',
        passwordHash: realPasswordHash,
      } as any);

      const req = createJsonRequest('http://localhost:3000/api/auth/login', {
        email: 'usuario@gusto.app',
        password: 'SenhaIncorreta@123',
      }, '10.10.2.2');

      const res = await loginHandler(req);
      const data = await res.json();

      expect(res.status).toBe(401);
      expect(data.error).toBe('Credenciais inválidas');
    });

    it('deve autenticar com credenciais corretas e retornar tokens (200)', async () => {
      const password = 'SenhaCorreta@2026';
      const realPasswordHash = await hashPassword(password);

      vi.mocked(prisma.user.findUnique).mockResolvedValue({
        id: 'user-login-id',
        name: 'Gusto Gourmet',
        email: 'gourmet@gusto.app',
        passwordHash: realPasswordHash,
        themePreference: 'OLED',
        avatarUrl: null,
      } as any);

      const req = createJsonRequest('http://localhost:3000/api/auth/login', {
        email: 'gourmet@gusto.app',
        password,
      }, '10.10.2.3');

      const res = await loginHandler(req);
      const data = await res.json();

      expect(res.status).toBe(200);
      expect(data.message).toContain('sucesso');
      expect(data.accessToken).toBeDefined();
      expect(data.refreshToken).toBeDefined();
      expect(data.user.name).toBe('Gusto Gourmet');
    });
  });

  describe('POST /api/auth/refresh', () => {
    it('deve rejeitar requisição sem refresh token (401)', async () => {
      const req = createJsonRequest('http://localhost:3000/api/auth/refresh', {});
      const res = await refreshHandler(req);
      const data = await res.json();

      expect(res.status).toBe(401);
      expect(data.error).toContain('não fornecido');
    });

    it('deve rejeitar refresh token inválido ou revogado (401)', async () => {
      vi.mocked(rotateRefreshToken).mockResolvedValue(null);

      const req = createJsonRequest('http://localhost:3000/api/auth/refresh', {
        refreshToken: 'token-invalido',
      });
      const res = await refreshHandler(req);
      const data = await res.json();

      expect(res.status).toBe(401);
      expect(data.error).toContain('inválido, expirado ou revogado');
    });

    it('deve rotacionar token com sucesso via body ou cookie (200)', async () => {
      vi.mocked(rotateRefreshToken).mockResolvedValue({
        accessToken: 'novo-access-token',
        refreshToken: 'novo-refresh-token',
        userId: 'u123',
      });

      const req = createJsonRequest('http://localhost:3000/api/auth/refresh', {}, '10.10.3.1', 'cookie-refresh-token');
      const res = await refreshHandler(req);
      const data = await res.json();

      expect(res.status).toBe(200);
      expect(data.accessToken).toBe('novo-access-token');
      expect(data.refreshToken).toBe('novo-refresh-token');
    });
  });

  describe('POST /api/auth/forgot-password', () => {
    it('deve rejeitar e-mail inválido com 400', async () => {
      const req = createJsonRequest('http://localhost:3000/api/auth/forgot-password', { email: 'invalido' }, '10.10.4.1');
      const res = await forgotPasswordHandler(req);
      const data = await res.json();

      expect(res.status).toBe(400);
      expect(data.error).toBe('E-mail inválido');
    });

    it('deve gerar OTP e responder com sucesso quando e-mail existir (200)', async () => {
      vi.mocked(prisma.user.findUnique).mockResolvedValue({
        id: 'u-forgot-1',
        email: 'recupera@gusto.app',
      } as any);
      vi.mocked(createPasswordResetOtp).mockResolvedValue('123456');

      const req = createJsonRequest('http://localhost:3000/api/auth/forgot-password', { email: 'recupera@gusto.app' }, '10.10.4.2');
      const res = await forgotPasswordHandler(req);
      const data = await res.json();

      expect(res.status).toBe(200);
      expect(data.message).toContain('código de verificação');
    });
  });

  describe('POST /api/auth/reset-password', () => {
    it('deve rejeitar payload inválido (OTP com tamanho diferente de 6) com 400', async () => {
      const req = createJsonRequest('http://localhost:3000/api/auth/reset-password', {
        email: 'recupera@gusto.app',
        otp: '123', // menor que 6
        newPassword: 'NovaSenha@2026',
      }, '10.10.5.1');

      const res = await resetPasswordHandler(req);
      const data = await res.json();

      expect(res.status).toBe(400);
      expect(data.details).toBeDefined();
    });

    it('deve rejeitar quando OTP estiver expirado ou incorreto (400)', async () => {
      vi.mocked(verifyAndConsumeOtp).mockResolvedValue(null);

      const req = createJsonRequest('http://localhost:3000/api/auth/reset-password', {
        email: 'recupera@gusto.app',
        otp: '999999',
        newPassword: 'NovaSenha@2026',
      }, '10.10.5.2');

      const res = await resetPasswordHandler(req);
      const data = await res.json();

      expect(res.status).toBe(400);
      expect(data.error).toContain('inválido ou expirado');
    });

    it('deve redefinir senha com sucesso e revogar refresh tokens ativos (200)', async () => {
      vi.mocked(verifyAndConsumeOtp).mockResolvedValue('u-reset-id');

      const req = createJsonRequest('http://localhost:3000/api/auth/reset-password', {
        email: 'recupera@gusto.app',
        otp: '123456',
        newPassword: 'NovaSenhaForte@2026',
      }, '10.10.5.3');

      const res = await resetPasswordHandler(req);
      const data = await res.json();

      expect(res.status).toBe(200);
      expect(data.message).toContain('redefinida com sucesso');
      expect(prisma.$transaction).toHaveBeenCalled();
    });
  });
});
