import bcrypt from 'bcryptjs';
import jwt from 'jsonwebtoken';
import { prisma } from './prisma';

const ACCESS_SECRET = process.env.JWT_ACCESS_SECRET || 'fallback_access_secret_for_gusto_recipes_123';
const REFRESH_SECRET = process.env.JWT_REFRESH_SECRET || 'fallback_refresh_secret_for_gusto_recipes_456';

const SALT_ROUNDS = 12;
const ACCESS_TOKEN_EXPIRATION = '15m';
const REFRESH_TOKEN_EXPIRATION_DAYS = 30;

export interface TokenPayload {
  userId: string;
  email: string;
}

export async function hashPassword(password: string): Promise<string> {
  return bcrypt.hash(password, SALT_ROUNDS);
}

export async function verifyPassword(password: string, hash: string): Promise<boolean> {
  return bcrypt.compare(password, hash);
}

export function generateAccessToken(payload: TokenPayload): string {
  return jwt.sign(payload, ACCESS_SECRET, { expiresIn: ACCESS_TOKEN_EXPIRATION });
}

export function generateRefreshToken(payload: TokenPayload): string {
  return jwt.sign(payload, REFRESH_SECRET, { expiresIn: `${REFRESH_TOKEN_EXPIRATION_DAYS}d` });
}

export function verifyAccessToken(token: string): TokenPayload | null {
  try {
    return jwt.verify(token, ACCESS_SECRET) as TokenPayload;
  } catch {
    return null;
  }
}

export function verifyRefreshToken(token: string): TokenPayload | null {
  try {
    return jwt.verify(token, REFRESH_SECRET) as TokenPayload;
  } catch {
    return null;
  }
}

/**
 * Persists a new refresh token and invalidates old ones if needed (rotation)
 */
export async function saveRefreshToken(userId: string, token: string): Promise<void> {
  const expiresAt = new Date();
  expiresAt.setDate(expiresAt.getDate() + REFRESH_TOKEN_EXPIRATION_DAYS);

  await prisma.refreshToken.create({
    data: {
      userId,
      token,
      expiresAt,
    },
  });
}

/**
 * Rotates a refresh token: verifies active token, marks it revoked, issues fresh pair
 */
export async function rotateRefreshToken(oldToken: string): Promise<{ accessToken: string; refreshToken: string; userId: string } | null> {
  const payload = verifyRefreshToken(oldToken);
  if (!payload) return null;

  const storedToken = await prisma.refreshToken.findUnique({
    where: { token: oldToken },
  });

  if (!storedToken || storedToken.revoked || storedToken.expiresAt < new Date()) {
    // If a revoked token is used, trigger security purge of all tokens for this user
    if (storedToken?.revoked) {
      await prisma.refreshToken.deleteMany({ where: { userId: payload.userId } });
    }
    return null;
  }

  // Revoke old token
  await prisma.refreshToken.update({
    where: { id: storedToken.id },
    data: { revoked: true },
  });

  // Issue new tokens
  const newAccessToken = generateAccessToken({ userId: payload.userId, email: payload.email });
  const newRefreshToken = generateRefreshToken({ userId: payload.userId, email: payload.email });

  await saveRefreshToken(payload.userId, newRefreshToken);

  return {
    accessToken: newAccessToken,
    refreshToken: newRefreshToken,
    userId: payload.userId,
  };
}

/**
 * Secure OTP Generation (6-digit numeric) with 10-minute expiry
 */
export function generateOtpCode(): string {
  const otp = Math.floor(100000 + Math.random() * 900000).toString();
  return otp;
}

export async function createPasswordResetOtp(userId: string, email: string): Promise<string> {
  // Invalidate previous unconsumed OTPs for this email
  await prisma.passwordResetOtp.updateMany({
    where: { email, consumed: false },
    data: { consumed: true },
  });

  const rawOtp = generateOtpCode();
  const codeHash = await hashPassword(rawOtp);
  const expiresAt = new Date(Date.now() + 10 * 60 * 1000); // 10 minutes

  await prisma.passwordResetOtp.create({
    data: {
      userId,
      email,
      codeHash,
      expiresAt,
    },
  });

  return rawOtp;
}

export async function verifyAndConsumeOtp(email: string, otp: string): Promise<string | null> {
  const validOtps = await prisma.passwordResetOtp.findMany({
    where: {
      email,
      consumed: false,
      expiresAt: { gt: new Date() },
    },
    orderBy: { createdAt: 'desc' },
    take: 3,
  });

  for (const record of validOtps) {
    const isMatch = await verifyPassword(otp, record.codeHash);
    if (isMatch) {
      // Mark as consumed
      await prisma.passwordResetOtp.update({
        where: { id: record.id },
        data: { consumed: true },
      });
      return record.userId;
    }
  }

  return null;
}
