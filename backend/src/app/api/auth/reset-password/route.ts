import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { prisma } from '@/lib/prisma';
import { hashPassword, verifyAndConsumeOtp } from '@/lib/auth';
import { checkRateLimit } from '@/lib/rate-limit';

const resetPasswordSchema = z.object({
  email: z.string().email('E-mail inválido').toLowerCase().trim(),
  otp: z.string().length(6, 'O código de verificação deve conter exatamente 6 dígitos'),
  newPassword: z
    .string()
    .min(8, 'A nova senha deve ter no mínimo 8 caracteres')
    .regex(/[A-Z]/, 'A senha deve conter ao menos uma letra maiúscula')
    .regex(/[0-9]/, 'A senha deve conter ao menos um número'),
});

export async function POST(req: NextRequest) {
  const rateLimit = checkRateLimit(req, 'auth_reset_password', { limit: 5, windowMs: 15 * 60 * 1000 });
  if (!rateLimit.allowed) {
    return NextResponse.json(
      { error: 'Muitas tentativas. Tente novamente mais tarde.' },
      { status: 429 }
    );
  }

  try {
    const body = await req.json();
    const validated = resetPasswordSchema.safeParse(body);

    if (!validated.success) {
      const fieldErrors = validated.error.flatten().fieldErrors;
      const firstError = Object.values(fieldErrors).flat()[0] || 'Dados inválidos';
      return NextResponse.json(
        { error: firstError, details: fieldErrors },
        { status: 400 }
      );
    }

    const { email, otp, newPassword } = validated.data;

    // Verify and atomically mark OTP as consumed
    const userId = await verifyAndConsumeOtp(email, otp);
    if (!userId) {
      return NextResponse.json(
        { error: 'Código de verificação inválido ou expirado.' },
        { status: 400 }
      );
    }

    const newHash = await hashPassword(newPassword);

    // Update user password and revoke all active refresh tokens for maximum security
    await prisma.$transaction([
      prisma.user.update({
        where: { id: userId },
        data: { passwordHash: newHash },
      }),
      prisma.refreshToken.updateMany({
        where: { userId, revoked: false },
        data: { revoked: true },
      }),
    ]);

    return NextResponse.json({
      message: 'Senha redefinida com sucesso. Por favor, faça login com sua nova senha.',
    });
  } catch (error) {
    console.error('Reset password error:', error);
    return NextResponse.json({ error: 'Erro ao redefinir senha' }, { status: 500 });
  }
}
