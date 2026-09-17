const resendApiKey = process.env.RESEND_API_KEY;
const defaultSender = process.env.EMAIL_FROM || 'Gusto <onboarding@resend.dev>';

interface SendPasswordResetEmailParams {
  to: string;
  otpCode: string;
}

/**
 * Dispara e-mail transacional de recuperação de senha utilizando a API REST oficial do Resend.
 */
export async function sendPasswordResetEmail({
  to,
  otpCode,
}: SendPasswordResetEmailParams): Promise<{ success: boolean; error?: string }> {
  if (!resendApiKey) {
    console.warn(
      `[EMAIL] RESEND_API_KEY não configurada no .env. Código OTP para ${to}: ${otpCode}`
    );
    return { success: true };
  }

  const html = `
    <!DOCTYPE html>
    <html lang="pt-BR">
    <head>
      <meta charset="utf-8">
      <meta name="viewport" content="width=device-width, initial-scale=1.0">
      <title>Recuperação de Senha - Gusto</title>
    </head>
    <body style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #0d0d0d; color: #f5f5f5; margin: 0; padding: 40px 20px;">
      <div style="max-width: 480px; margin: 0 auto; background-color: #171717; border-radius: 16px; border: 1px solid #262626; padding: 36px 28px; text-align: center;">
        <div style="font-size: 26px; font-weight: 700; letter-spacing: -0.5px; margin-bottom: 20px; color: #ffffff;">
          Gusto
        </div>
        
        <h1 style="font-size: 19px; font-weight: 600; margin-bottom: 12px; color: #e5e5e5;">
          Recuperação de Senha
        </h1>
        
        <p style="font-size: 14px; color: #a3a3a3; line-height: 1.6; margin-bottom: 24px;">
          Recebemos uma solicitação para redefinir a senha da sua conta Gusto. Use o código de verificação abaixo no aplicativo:
        </p>
        
        <div style="background-color: #222222; border: 1px solid #333333; border-radius: 12px; padding: 18px 24px; margin-bottom: 24px; display: inline-block;">
          <span style="font-family: 'Courier New', Courier, monospace; font-size: 32px; font-weight: 700; letter-spacing: 8px; color: #ffffff;">
            ${otpCode}
          </span>
        </div>
        
        <p style="font-size: 13px; color: #737373; line-height: 1.5; margin-bottom: 24px;">
          Este código é válido por <strong>10 minutos</strong>. Se você não solicitou a redefinição de senha, nenhuma ação é necessária e sua conta permanece segura.
        </p>
        
        <div style="border-top: 1px solid #262626; padding-top: 20px; font-size: 12px; color: #525252;">
          © ${new Date().getFullYear()} Gusto. Todos os direitos reservados.
        </div>
      </div>
    </body>
    </html>
  `;

  try {
    const response = await fetch('https://api.resend.com/emails', {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${resendApiKey}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        from: defaultSender,
        to: [to],
        subject: 'Seu código de recuperação de senha — Gusto',
        html,
      }),
    });

    const data = await response.json();

    if (!response.ok) {
      console.error('[EMAIL] Erro da API do Resend:', data);
      return { success: false, error: data?.message || 'Falha ao enviar e-mail com Resend' };
    }

    console.log(`[EMAIL] E-mail de recuperação enviado com sucesso para ${to} (ID: ${data?.id})`);
    return { success: true };
  } catch (err: any) {
    console.error('[EMAIL] Exceção inesperada ao enviar e-mail:', err);
    return { success: false, error: err?.message || 'Falha ao disparar e-mail' };
  }
}
