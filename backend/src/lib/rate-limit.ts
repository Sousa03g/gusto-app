import { NextRequest } from 'next/server';

interface RateLimitTracker {
  count: number;
  resetTime: number;
}

const rateLimitStore = new Map<string, RateLimitTracker>();

/**
 * Clean up expired windows every 5 minutes to prevent memory leak
 */
setInterval(() => {
  const now = Date.now();
  for (const [key, record] of rateLimitStore.entries()) {
    if (record.resetTime < now) {
      rateLimitStore.delete(key);
    }
  }
}, 5 * 60 * 1000);

export interface RateLimitOptions {
  limit: number;      // max requests
  windowMs: number;   // time window in milliseconds
}

export function checkRateLimit(
  req: NextRequest,
  prefix: string,
  options: RateLimitOptions = { limit: 10, windowMs: 60 * 1000 }
): { allowed: boolean; remaining: number; resetTime: number } {
  // Extract client IP address
  const forwardedFor = req.headers.get('x-forwarded-for');
  const realIp = req.headers.get('x-real-ip');
  const ip = (forwardedFor ? forwardedFor.split(',')[0].trim() : realIp) || '127.0.0.1';

  const key = `${prefix}:${ip}`;
  const now = Date.now();
  const current = rateLimitStore.get(key);

  if (!current || current.resetTime < now) {
    rateLimitStore.set(key, { count: 1, resetTime: now + options.windowMs });
    return { allowed: true, remaining: options.limit - 1, resetTime: now + options.windowMs };
  }

  if (current.count >= options.limit) {
    return { allowed: false, remaining: 0, resetTime: current.resetTime };
  }

  current.count += 1;
  return { allowed: true, remaining: options.limit - current.count, resetTime: current.resetTime };
}
