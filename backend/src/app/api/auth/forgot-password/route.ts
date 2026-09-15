import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { prisma } from '@/lib/prisma';
import { createPasswordResetOtp } from '@/lib/auth';
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

    // Prevent account enumeration by always returning success response
    if (user) {
      const otpCode = await createPasswordResetOtp(user.id, email);

      // In production, send via Sendgrid / Resend / AWS SES.
      // We log to server console for development/test purposes.
      console.log(`[AUTH] Código OTP gerado para ${email}: ${otpCode}`);
    }

    return NextResponse.json({
      message: 'Se este e-mail estiver cadastrado, um código de verificação seguro (OTP) foi enviado.',
    });
  } catch (error) {
    console.error('Forgot password error:', error);
    return NextResponse.json({ error: 'Erro ao processar recuperação' }, { status: 500 });
  }
}
