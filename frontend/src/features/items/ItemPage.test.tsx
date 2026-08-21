import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http } from 'msw';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ItemPage } from '@/features/items/ItemPage';
import { error, ok, server } from '@/test/msw';

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={['/tasks/task-1/items']}>
        <Routes><Route path="/tasks/:taskId/items" element={<ItemPage />} /></Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

const item = { id: 'item-1', taskId: 'task-1', title: '已有题目', content: '正文', analysis: null, externalUrl: null, sortOrder: 1, status: 'pending' as const };

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
