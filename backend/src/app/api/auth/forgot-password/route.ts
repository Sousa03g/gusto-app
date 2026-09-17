import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { prisma } from '@/lib/prisma';
import { createPasswordResetOtp } from '@/lib/auth';
import { sendPasswordResetEmail } from '@/lib/email';
import { checkRateLimit } from '@/lib/rate-limit';

const forgotPasswordSchema = z.object({
  email: z.string().email('E-mail inválido').toLowerCase().trim(),
});

export async function POST(req: NextRequest) {
  const rateLimit = checkRateLimit(req, 'auth_forgot_password', { limit: 3, windowMs: 15 * 60 * 1000 });
  if (!rateLimit.allowed) {
    return NextResponse.json(
      { error: 'Limite de solicitações de redefinição excedido. Tente novamente em 15 minutos.' },
      { status: 429 }
    );
  }

  try {
    const body = await req.json();
    const validated = forgotPasswordSchema.safeParse(body);

    if (!validated.success) {
      return NextResponse.json({ error: 'E-mail inválido' }, { status: 400 });
    }

    const { email } = validated.data;
    const user = await prisma.user.findUnique({ where: { email } });

    let devOtp: string | null = null;

    if (user) {
      const otpCode = await createPasswordResetOtp(user.id, email);
      devOtp = otpCode;
      console.log(`[AUTH] Código OTP gerado para ${email}: ${otpCode}`);

      // Dispara o envio do e-mail com Resend
      await sendPasswordResetEmail({ to: email, otpCode });
    }

    const isDev = process.env.NODE_ENV !== 'production' || process.env.NEXT_PUBLIC_DEBUG_OTP === 'true';

    return NextResponse.json({
      message: 'Se este e-mail estiver cadastrado, um código de verificação seguro (OTP) foi enviado.',
      devOtp: isDev ? devOtp : undefined,
    });
  } catch (error) {
    console.error('Forgot password error:', error);
    return NextResponse.json({ error: 'Erro ao processar recuperação de senha' }, { status: 500 });
  }
}
