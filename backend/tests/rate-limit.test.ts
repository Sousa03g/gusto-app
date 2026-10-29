import { describe, it, expect, beforeEach } from 'vitest';
import { checkRateLimit } from '@/lib/rate-limit';

function createMockRequest(ip: string): any {
  return {
    headers: {
      get: (header: string) => {
        if (header.toLowerCase() === 'x-forwarded-for') return ip;
        if (header.toLowerCase() === 'x-real-ip') return ip;
        return null;
      },
    },
  };
}

describe('Rate Limiter - Proteção contra Abuso e Ataques de Força Bruta', () => {
  const options = { limit: 3, windowMs: 2000 };

  it('deve permitir requisições abaixo do limite e decrementar o saldo restante', () => {
    const req = createMockRequest('192.168.1.50');
    const prefix = 'auth-login-test';

    const r1 = checkRateLimit(req, prefix, options);
    expect(r1.allowed).toBe(true);
    expect(r1.remaining).toBe(2);

    const r2 = checkRateLimit(req, prefix, options);
    expect(r2.allowed).toBe(true);
    expect(r2.remaining).toBe(1);

    const r3 = checkRateLimit(req, prefix, options);
    expect(r3.allowed).toBe(true);
    expect(r3.remaining).toBe(0);
  });

  it('deve bloquear imediatamente requisições que excederem o limite (allowed = false)', () => {
    const req = createMockRequest('10.0.0.99');
    const prefix = 'api-block-test';

    // 3 requisições permitidas
    checkRateLimit(req, prefix, options);
    checkRateLimit(req, prefix, options);
    checkRateLimit(req, prefix, options);

    // 4ª requisição deve ser bloqueada
    const r4 = checkRateLimit(req, prefix, options);
    expect(r4.allowed).toBe(false);
    expect(r4.remaining).toBe(0);
  });

  it('deve isolar a contagem entre diferentes endereços IP', () => {
    const reqA = createMockRequest('1.1.1.1');
    const reqB = createMockRequest('2.2.2.2');
    const prefix = 'isolated-ips';

    // Esgota limite para o IP A
    checkRateLimit(reqA, prefix, options);
    checkRateLimit(reqA, prefix, options);
    checkRateLimit(reqA, prefix, options);
    const rBlockedA = checkRateLimit(reqA, prefix, options);
    expect(rBlockedA.allowed).toBe(false);

    // IP B deve continuar com cota limpa
    const rInitialB = checkRateLimit(reqB, prefix, options);
    expect(rInitialB.allowed).toBe(true);
    expect(rInitialB.remaining).toBe(2);
  });

  it('deve utilizar IP padrão (127.0.0.1) se headers de IP estiverem ausentes', () => {
    const emptyReq = {
      headers: {
        get: () => null,
      },
    } as any;

    const r = checkRateLimit(emptyReq, 'fallback-ip-test', { limit: 5, windowMs: 5000 });
    expect(r.allowed).toBe(true);
    expect(r.remaining).toBe(4);
  });
});
