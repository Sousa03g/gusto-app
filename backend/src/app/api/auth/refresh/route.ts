import { NextRequest, NextResponse } from 'next/server';
import { rotateRefreshToken } from '@/lib/auth';

export async function POST(req: NextRequest) {
  try {
    // Check both Authorization header / JSON body and HttpOnly cookie
    let refreshToken: string | null = null;

    const cookieToken = req.cookies.get('refreshToken')?.value;
    if (cookieToken) {
      refreshToken = cookieToken;
    } else {
      const body = await req.json().catch(() => ({}));
      refreshToken = body.refreshToken || null;
    }

    if (!refreshToken) {
      return NextResponse.json({ error: 'Refresh token não fornecido' }, { status: 401 });
    }

    const rotated = await rotateRefreshToken(refreshToken);
    if (!rotated) {
      return NextResponse.json(
        { error: 'Refresh token inválido, expirado ou revogado' },
        { status: 401 }
      );
    }

    const response = NextResponse.json({
      accessToken: rotated.accessToken,
      refreshToken: rotated.refreshToken,
    });

    response.cookies.set('refreshToken', rotated.refreshToken, {
      httpOnly: true,
      secure: process.env.NODE_ENV === 'production',
      sameSite: 'strict',
      maxAge: 30 * 24 * 60 * 60,
      path: '/',
    });

    return response;
  } catch (error) {
    console.error('Refresh token error:', error);
    return NextResponse.json({ error: 'Erro ao renovar token' }, { status: 500 });
  }
}
