export type UserView = { id: string; email: string; timezone: string };
export type Envelope<T> = { data: T; requestId: string };
export type ApiErrorBody = { error?: { code?: string; message?: string }; requestId?: string };

export class ApiError extends Error {
  constructor(public readonly status: number, public readonly code: string, message: string, public readonly requestId?: string) {
    super(message);
    this.name = 'ApiError';
  }
}

let csrfToken: string | undefined;

function resetCsrf() {
  csrfToken = undefined;
}

async function readBody<T>(response: Response): Promise<T> {
  const body = (await response.json()) as Envelope<T> & ApiErrorBody;
  if (!response.ok) {
    throw new ApiError(response.status, body.error?.code ?? 'HTTP_ERROR', body.error?.message ?? '请求失败', body.requestId);
  }
  return body.data;
}

export async function getCsrf(): Promise<string> {
  const response = await fetch('/api/v1/auth/csrf', { credentials: 'include' });
  const data = await readBody<{ csrfToken: string }>(response);
  csrfToken = data.csrfToken;
  return csrfToken;
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const method = (init.method ?? 'GET').toUpperCase();
  const headers = new Headers(init.headers);
  if (init.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json');
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    if (!csrfToken) await getCsrf();
    headers.set('X-CSRF-Token', csrfToken!);
  }
  let response = await fetch(`/api/v1${path}`, { ...init, method, headers, credentials: 'include' });
  if (response.status === 403 && !['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    resetCsrf();
    await getCsrf();
    headers.set('X-CSRF-Token', csrfToken!);
    response = await fetch(`/api/v1${path}`, { ...init, method, headers, credentials: 'include' });
  }
  return readBody<T>(response);
}

export const authApi = {
  register: (body: { email: string; password: string; confirmPassword: string }) => request<{ user: UserView }>('/auth/register', { method: 'POST', body: JSON.stringify(body) }),
  login: (body: { email: string; password: string }) => request<{ user: UserView }>('/auth/login', { method: 'POST', body: JSON.stringify(body) }),
  me: () => request<UserView>('/auth/me'),
  logout: () => request<{ loggedOut: boolean }>('/auth/logout', { method: 'POST' }),
};
