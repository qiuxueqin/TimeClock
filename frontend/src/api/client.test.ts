import { describe, expect, it, vi, beforeEach } from 'vitest';
import { authApi, getCsrf } from '@/api/client';

describe('auth api client', () => {
  beforeEach(() => vi.restoreAllMocks());

  it('uses cookies and injects CSRF for writes', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(new Response(JSON.stringify({ data: { csrfToken: 'csrf-1' }, requestId: 'r1' }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ data: { user: { id: '1', email: 'a@example.com', timezone: 'Asia/Shanghai' } }, requestId: 'r2' }), { status: 200 }));
    await getCsrf();
    await authApi.login({ email: 'a@example.com', password: 'CorrectHorse1!' });
    expect(fetchMock.mock.calls[0][1]).toMatchObject({ credentials: 'include' });
    expect(fetchMock.mock.calls[1][1]).toMatchObject({ credentials: 'include' });
    expect(new Headers(fetchMock.mock.calls[1][1]?.headers).get('X-CSRF-Token')).toBe('csrf-1');
  });

  it('maps stable error envelopes', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(
      JSON.stringify({ error: { code: 'INVALID_CREDENTIALS', message: '邮箱或密码不正确' }, requestId: 'r3' }), { status: 401 }));
    await expect(authApi.me()).rejects.toMatchObject({ status: 401, code: 'INVALID_CREDENTIALS', requestId: 'r3' });
  });
});
