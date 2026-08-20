export type UserView = { id: string; email: string; timezone: string };

export type TaskStatus = 'draft' | 'active';
export type TaskType = 'checklist';
export type ScheduleType = 'daily';
export type TaskView = {
  id: string; name: string; description: string | null; type: TaskType; status: TaskStatus;
  startDate: string; endDate: string | null; scheduleType: ScheduleType; timezone: string;
  dailyTargetCount: number; ended: boolean; itemCount: number; completedItemCount: number;
};
export type TaskPage = { items: TaskView[]; page: number; pageSize: number; total: number };
export type TaskCreateRequest = {
  name: string; description?: string; type: TaskType; startDate: string; endDate?: string | null;
  scheduleType: ScheduleType; timezone: string; dailyTargetCount: number;
};
export type TaskUpdateRequest = Partial<Pick<TaskCreateRequest, 'name' | 'description' | 'startDate' | 'endDate' | 'scheduleType' | 'timezone' | 'dailyTargetCount'>>;

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
  if (response.status === 204) return undefined as T;
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

export const taskApi = {
  list: (params: { status?: TaskStatus; page?: number; pageSize?: number } = {}) => {
    const query = new URLSearchParams();
    if (params.status) query.set('status', params.status);
    query.set('page', String(params.page ?? 1));
    query.set('pageSize', String(params.pageSize ?? 20));
    return request<TaskPage>(`/tasks?${query.toString()}`);
  },
  get: (id: string) => request<TaskView>(`/tasks/${encodeURIComponent(id)}`),
  create: (body: TaskCreateRequest) => request<TaskView>('/tasks', { method: 'POST', body: JSON.stringify(body) }),
  update: (id: string, body: TaskUpdateRequest) => request<TaskView>(`/tasks/${encodeURIComponent(id)}`, { method: 'PATCH', body: JSON.stringify(body) }),
  remove: (id: string) => request<void>(`/tasks/${encodeURIComponent(id)}`, { method: 'DELETE' }),
};

export const authApi = {
  register: (body: { email: string; password: string; confirmPassword: string }) => request<{ user: UserView }>('/auth/register', { method: 'POST', body: JSON.stringify(body) }),
  login: (body: { email: string; password: string }) => request<{ user: UserView }>('/auth/login', { method: 'POST', body: JSON.stringify(body) }),
  me: () => request<UserView>('/auth/me'),
  logout: () => request<{ loggedOut: boolean }>('/auth/logout', { method: 'POST' }),
};
