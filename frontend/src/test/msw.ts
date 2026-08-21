import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';

export const server = setupServer(
  http.get('/api/v1/auth/csrf', () => HttpResponse.json({ data: { csrfToken: 'test-csrf' }, requestId: 'csrf' })),
);

export function ok<T>(data: T, requestId = 'test-request') {
  return HttpResponse.json({ data, requestId });
}

export function error(status: number, code: string, message: string, requestId = 'test-error') {
  return HttpResponse.json({ error: { code, message }, requestId }, { status });
}
