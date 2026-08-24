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

export type ItemStatus = 'pending' | 'completed';
export type ItemView = {
  id: string; taskId?: string; title: string; content: string | null; analysis: string | null; externalUrl: string | null;
  sortOrder: number; status: ItemStatus; solutionText?: string | null; completedAt?: string | null;
};
export type ItemPage = { items: ItemView[]; page: number; pageSize: number; total: number };
export type TodayItem = { item: ItemView; assigned: boolean; belongsToToday: boolean };
export type TodayItemsResponse = { task: TaskView; plannedCount: number; completedCount: number; items: TodayItem[] };
export type PasteCandidate = { title: string; content?: string | null; analysis?: string | null; externalUrl?: string | null };
export type PastePreviewResponse = { totalLines: number; validLines: number; errorLines: { lineNumber: number; reason: string }[]; candidates: PasteCandidate[] };
export type XlsxCandidate = { title: string; content?: string | null; analysis?: string | null; link?: string | null; order?: number | null; duplicate?: boolean };
export type XlsxPreviewResponse = { totalRows: number; validRows: number; errorRows: { rowNumber: number; reason: string }[]; candidates: XlsxCandidate[] };
export type DashboardTodayResponse = { date: string; todayCount: number; completedCount: number; pendingCount: number; completionRate: number; tasks: { task: TaskView; status: string; completedCount: number; plannedCount: number; reminderText?: string | null }[]; currentStreak: number; longestStreak: number };

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
  const isFormData = typeof FormData !== 'undefined' && init.body instanceof FormData;
  if (init.body && !headers.has('Content-Type') && !isFormData) headers.set('Content-Type', 'application/json');
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

function idempotencyKey(headers?: HeadersInit) {
  const result = new Headers(headers);
  result.set('Idempotency-Key', crypto.randomUUID());
  return result;
}

export const itemApi = {
  list: (taskId: string, params: { status?: ItemStatus; page?: number; pageSize?: number } = {}) => {
    const query = new URLSearchParams({ page: String(params.page ?? 1), pageSize: String(params.pageSize ?? 20) });
    if (params.status) query.set('status', params.status);
    return request<ItemPage>(`/tasks/${encodeURIComponent(taskId)}/items?${query}`);
  },
  create: (taskId: string, body: { title: string; content?: string; analysis?: string; externalUrl?: string }) => request<ItemView>(`/tasks/${encodeURIComponent(taskId)}/items`, { method: 'POST', body: JSON.stringify(body) }),
  pastePreview: (taskId: string, text: string) => request<PastePreviewResponse>(`/tasks/${encodeURIComponent(taskId)}/items/paste-preview`, { method: 'POST', body: JSON.stringify({ text }) }),
  pasteConfirm: (taskId: string, candidates: PasteCandidate[]) => request<ItemView[]>(`/tasks/${encodeURIComponent(taskId)}/items/paste-confirm`, { method: 'POST', headers: idempotencyKey(), body: JSON.stringify({ candidates }) }),
  today: (taskId: string) => request<TodayItemsResponse>(`/tasks/${encodeURIComponent(taskId)}/today-items`),
  update: (itemId: string, body: { title?: string; content?: string; analysis?: string; externalUrl?: string }) => request<ItemView>(`/items/${encodeURIComponent(itemId)}`, { method: 'PATCH', body: JSON.stringify(body) }),
};

export type SubmissionView = { itemId: string; solutionContent: string; status: ItemStatus };
export type CompletionResponse = { item: ItemView; plannedCount: number; completedCount: number; taskCompletedCount: number; checkinStatus: 'completed' | 'partial' };

export const submissionApi = {
  get: (itemId: string) => request<SubmissionView>(`/items/${encodeURIComponent(itemId)}/submission`),
  save: (itemId: string, solutionContent: string) => request<SubmissionView>(`/items/${encodeURIComponent(itemId)}/submission`, { method: 'PUT', body: JSON.stringify({ solutionContent }) }),
  complete: (itemId: string, solutionContent: string) => request<CompletionResponse>(`/items/${encodeURIComponent(itemId)}/complete`, { method: 'POST', headers: idempotencyKey(), body: JSON.stringify({ solutionContent }) }),
  reopen: (itemId: string) => request<CompletionResponse>(`/items/${encodeURIComponent(itemId)}/reopen`, { method: 'POST', headers: idempotencyKey(), body: JSON.stringify({}) }),
};

export const importApi = {
  xlsxPreview: (taskId: string, file: File) => { const body = new FormData(); body.append('file', file); return request<XlsxPreviewResponse>(`/tasks/${encodeURIComponent(taskId)}/imports/xlsx/preview`, { method: 'POST', body }); },
  xlsxConfirm: (taskId: string, candidates: XlsxCandidate[]) => request<{ createdCount: number; skippedCount: number }>(`/tasks/${encodeURIComponent(taskId)}/imports/xlsx/confirm`, { method: 'POST', headers: idempotencyKey(), body: JSON.stringify({ candidates }) }),
};

export const dashboardApi = { today: () => request<DashboardTodayResponse>('/dashboard/today') };

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
  activate: (id: string) => request<TaskView>('/tasks/' + encodeURIComponent(id) + '/activate', { method: 'POST' }),
};

export const authApi = {
  register: (body: { email: string; password: string; confirmPassword: string }) => request<{ user: UserView }>('/auth/register', { method: 'POST', body: JSON.stringify(body) }),
  login: (body: { email: string; password: string }) => request<{ user: UserView }>('/auth/login', { method: 'POST', body: JSON.stringify(body) }),
  me: () => request<UserView>('/auth/me'),
  logout: () => request<{ loggedOut: boolean }>('/auth/logout', { method: 'POST' }),
};
