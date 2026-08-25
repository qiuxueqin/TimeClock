import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http } from 'msw';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { type ItemView } from '@/api/client';
import { ItemPage } from '@/features/items/ItemPage';
import { error, ok, server } from '@/test/msw';

function renderPage(route = '/tasks/task-1/items') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[route]}>
        <Routes>
          <Route path="/tasks/:taskId/items" element={<ItemPage />} />
          <Route path="/tasks/:taskId/today" element={<ItemPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

const item = { id: 'item-1', taskId: 'task-1', title: '已有题目', content: '正文', analysis: null, externalUrl: null, sortOrder: 1, status: 'pending' as const };
const taskView = { id: 'task-1', name: '任务一', description: null, type: 'checklist' as const, status: 'active' as const, startDate: '2026-08-01', endDate: null, scheduleType: 'daily' as const, timezone: 'Asia/Shanghai', dailyTargetCount: 2, ended: false, itemCount: 5, completedItemCount: 1 };
const solvedCompleted = { ...item, title: '题目一', status: 'completed' as const, solutionText: '已写题解' };
const completionResponse = (checkinStatus: 'completed' | 'partial') => ({ item: solvedCompleted, plannedCount: 2, completedCount: 2, taskCompletedCount: 3, checkinStatus });

afterEach(() => vi.restoreAllMocks());

describe('ItemPage', () => {
  it('disables creation for an empty or whitespace-only title', async () => {
    server.use(http.get('/api/v1/tasks/task-1/items', () => ok({ items: [], page: 1, pageSize: 20, total: 0 })));
    renderPage();
    await screen.findByText('暂无条目');
    const title = screen.getByRole('textbox', { name: '新条目标题' });
    const add = screen.getByRole('button', { name: /添\s*加/ });
    expect(add).toBeDisabled();
    await userEvent.type(title, '   ');
    expect(add).toBeDisabled();
  });

  it('renders paste preview errors and refetches items after confirmation', async () => {
    let listCalls = 0;
    server.use(
      http.get('/api/v1/tasks/task-1/items', () => { listCalls += 1; return ok({ items: listCalls === 1 ? [] : [item], page: 1, pageSize: 20, total: listCalls === 1 ? 0 : 1 }); }),
      http.post('/api/v1/tasks/task-1/items/paste-preview', () => ok({ totalLines: 2, validLines: 1, errorLines: [{ lineNumber: 2, reason: '标题为空' }], candidates: [{ title: '新题目' }] })),
      http.post('/api/v1/tasks/task-1/items/paste-confirm', () => ok([item])),
    );
    renderPage();
    await screen.findByText('暂无条目');
    const user = userEvent.setup();
    await user.type(screen.getByRole('textbox', { name: '粘贴条目' }), '新题目\n');
    await user.click(screen.getByRole('button', { name: /预\s*览/ }));
    expect(await screen.findByText(/第2行：标题为空/)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /确认导入（1）/ }));
    await waitFor(() => expect(screen.getByText(/已有题目/)).toBeInTheDocument());
    expect(listCalls).toBe(2);
  });

  it('keeps the paste preview when confirmation fails', async () => {
    server.use(
      http.get('/api/v1/tasks/task-1/items', () => ok({ items: [], page: 1, pageSize: 20, total: 0 })),
      http.post('/api/v1/tasks/task-1/items/paste-preview', () => ok({ totalLines: 1, validLines: 1, errorLines: [], candidates: [{ title: '待确认题目' }] })),
      http.post('/api/v1/tasks/task-1/items/paste-confirm', () => error(409, 'CONFLICT', '确认失败')),
    );
    renderPage();
    await screen.findByText('暂无条目');
    const user = userEvent.setup();
    await user.type(screen.getByRole('textbox', { name: '粘贴条目' }), '待确认题目');
    await user.click(screen.getByRole('button', { name: /预\s*览/ }));
    const confirm = await screen.findByRole('button', { name: /确认导入（1）/ });
    await user.click(confirm);
    await waitFor(() => expect(screen.getByText('待确认题目')).toBeInTheDocument());
    expect(screen.getByRole('button', { name: /确认导入（1）/ })).toBeInTheDocument();
  });
});

describe('ItemPage 完成闭环', () => {
  function useItemsHandler(onCall?: (call: number) => ItemView) {
    let calls = 0;
    server.use(http.get('/api/v1/tasks/task-1/items', () => { calls += 1; const current = onCall ? onCall(calls) : item; return ok({ items: [current], page: 1, pageSize: 20, total: 1 }); }));
    return () => calls;
  }

  it('keeps 完成本题 disabled for blank solutions and sends no request', async () => {
    let completeCalls = 0;
    useItemsHandler();
    server.use(http.post('/api/v1/items/item-1/complete', () => { completeCalls += 1; return ok(completionResponse('partial')); }));
    renderPage();
    const box = await screen.findByRole('textbox', { name: '题解-item-1' });
    const completeButton = screen.getByRole('button', { name: '完成本题' });
    expect(completeButton).toBeDisabled();
    const user = userEvent.setup();
    await user.type(box, '   ');
    expect(completeButton).toBeDisabled();
    fireEvent.click(completeButton);
    expect(completeCalls).toBe(0);
  });

  it('sends exactly one complete request with Idempotency-Key and refreshes to completed state', async () => {
    const listCalls = useItemsHandler((call) => (call > 1 ? solvedCompleted : item));
    let idempotencyKey: string | null = null;
    let body = '';
    server.use(http.post('/api/v1/items/item-1/complete', async ({ request }) => { idempotencyKey = request.headers.get('Idempotency-Key'); body = await request.text(); return ok(completionResponse('partial')); }));
    renderPage();
    const user = userEvent.setup();
    await user.type(await screen.findByRole('textbox', { name: '题解-item-1' }), '我的题解');
    await user.click(screen.getByRole('button', { name: '完成本题' }));
    expect(await screen.findByRole('button', { name: '撤销完成' })).toBeInTheDocument();
    expect(screen.getAllByText('已完成').length).toBeGreaterThan(0);
    expect(JSON.parse(body)).toEqual({ solutionContent: '我的题解' });
    expect(idempotencyKey).toBeTruthy();
    expect(listCalls()).toBe(2);
  });

  it('prevents duplicate complete requests while the mutation is pending', async () => {
    let completeCalls = 0;
    let release: (() => void) | undefined;
    useItemsHandler((call) => (call > 1 ? solvedCompleted : item));
    server.use(http.post('/api/v1/items/item-1/complete', async () => { completeCalls += 1; await new Promise<void>((resolve) => { release = resolve; }); return ok(completionResponse('partial')); }));
    renderPage();
    const user = userEvent.setup();
    await user.type(await screen.findByRole('textbox', { name: '题解-item-1' }), '题解内容');
    const completeButton = screen.getByRole('button', { name: '完成本题' });
    await user.click(completeButton);
    await waitFor(() => expect(release).toBeDefined());
    expect(completeButton).toBeDisabled();
    fireEvent.click(completeButton);
    expect(completeCalls).toBe(1);
    release?.();
    await screen.findByRole('button', { name: '撤销完成' });
    expect(completeCalls).toBe(1);
  });

  it('shows 撤销完成 for completed items and keeps solution text after reopening', async () => {
    let reopenCalls = 0;
    const listCalls = useItemsHandler((call) => (call === 1 ? solvedCompleted : { ...solvedCompleted, status: 'pending' as const }));
    server.use(http.post('/api/v1/items/item-1/reopen', () => { reopenCalls += 1; return ok({ ...completionResponse('partial'), item: { ...solvedCompleted, status: 'pending' as const }, completedCount: 1 }); }));
    renderPage();
    expect(await screen.findByText(/题目一/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '撤销完成' })).toBeInTheDocument();
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: '撤销完成' }));
    await waitFor(() => expect(reopenCalls).toBe(1));
    await waitFor(() => expect(listCalls()).toBeGreaterThanOrEqual(2));
    expect(screen.getByRole('textbox', { name: '题解-item-1' })).toHaveValue('已写题解');
  });

  it('renders 参考解析 only when analysis exists', async () => {
    useItemsHandler(() => ({ ...item, analysis: '解析内容ABC' }));
    const first = renderPage();
    expect(await screen.findByText(/参考解析：解析内容ABC/)).toBeInTheDocument();
    first.unmount();
    useItemsHandler(() => item);
    renderPage();
    await screen.findByText(/已有题目/);
    expect(screen.queryByText(/^参考解析：/)).not.toBeInTheDocument();
  });

  it('renders today progress only on the /today route', async () => {
    server.use(
      http.get('/api/v1/tasks/task-1/items', () => ok({ items: [item], page: 1, pageSize: 20, total: 1 })),
      http.get('/api/v1/tasks/task-1/today-items', () => ok({ task: taskView, plannedCount: 2, completedCount: 1, items: [] })),
    );
    const todayView = renderPage('/tasks/task-1/today');
    expect(await screen.findByTestId('today-progress')).toHaveTextContent('今日进度：1/2');
    todayView.unmount();
    renderPage('/tasks/task-1/items');
    await screen.findByText(/已有题目/);
    expect(screen.queryByTestId('today-progress')).not.toBeInTheDocument();
  });

  // GATE-S5：任何打卡成功后精确刷新今日及相关详情缓存——只失效 dashboard/today，不波及无关查询。
  it('invalidates exactly the dashboard today cache after complete and reopen', async () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
    client.setQueryData(['dashboard', 'today'], { date: '2026-08-25' });
    client.setQueryData(['tasks'], { items: [] });
    let calls = 0;
    server.use(
      http.get('/api/v1/tasks/task-1/items', () => { calls += 1; return ok({ items: [calls === 1 ? item : solvedCompleted], page: 1, pageSize: 20, total: 1 }); }),
      http.post('/api/v1/items/item-1/complete', () => ok(completionResponse('completed'))),
      http.post('/api/v1/items/item-1/reopen', () => ok({ ...completionResponse('partial'), item: { ...solvedCompleted, status: 'pending' as const } })),
    );
    render(
      <QueryClientProvider client={client}>
        <MemoryRouter initialEntries={['/tasks/task-1/items']}>
          <Routes><Route path="/tasks/:taskId/items" element={<ItemPage />} /></Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    );
    const user = userEvent.setup();

    await user.type(await screen.findByRole('textbox', { name: '题解-item-1' }), '题解内容');
    await user.click(screen.getByRole('button', { name: '完成本题' }));
    await screen.findByRole('button', { name: '撤销完成' });
    expect(client.getQueryState(['dashboard', 'today'])?.isInvalidated).toBe(true);
    expect(client.getQueryState(['tasks'])?.isInvalidated).toBe(false);

    // 撤销同样回写缓存失效标记；重新写入今日数据后再次撤销仍精确失效。
    client.setQueryData(['dashboard', 'today'], { date: '2026-08-25' });
    await user.click(screen.getByRole('button', { name: '撤销完成' }));
    await waitFor(() => expect(calls).toBeGreaterThanOrEqual(3));
    expect(client.getQueryState(['dashboard', 'today'])?.isInvalidated).toBe(true);
    expect(client.getQueryState(['tasks'])?.isInvalidated).toBe(false);
  });
});
