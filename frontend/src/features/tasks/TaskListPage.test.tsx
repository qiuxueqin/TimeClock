import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http } from 'msw';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { TaskListPage } from '@/features/tasks/TaskListPage';
import { ok, server } from '@/test/msw';

const task = { id: 'task-9', name: '草稿任务', description: null, type: 'checklist' as const, status: 'draft' as const, startDate: '2026-08-24', endDate: null, scheduleType: 'daily' as const, timezone: 'Asia/Shanghai', dailyTargetCount: 2, ended: false, itemCount: 5, completedItemCount: 0 };

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(<QueryClientProvider client={client}><MemoryRouter initialEntries={['/tasks']}><TaskListPage /></MemoryRouter></QueryClientProvider>);
}

describe('TaskListPage', () => {
  it('activates a draft task via POST /tasks/:id/activate and refreshes its status', async () => {
    let listCalls = 0;
    let activatePath = '';
    let activateMethod = '';
    server.use(
      http.get('/api/v1/tasks', () => { listCalls += 1; return ok({ items: [{ ...task, status: listCalls === 1 ? ('draft' as const) : ('active' as const) }], page: 1, pageSize: 20, total: 1 }); }),
      http.post('/api/v1/tasks/task-9/activate', ({ request }) => { activatePath = new URL(request.url).pathname; activateMethod = request.method; return ok({ ...task, status: 'active' }); }),
    );
    renderPage();
    await screen.findByText('草稿任务');
    expect(screen.getByText('草稿')).toBeInTheDocument();
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: '启用' }));
    await waitFor(() => expect(activateMethod).toBe('POST'));
    expect(activatePath).toBe('/api/v1/tasks/task-9/activate');
    expect(await screen.findByText('已启用')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '启用' })).not.toBeInTheDocument();
    expect(listCalls).toBeGreaterThanOrEqual(2);
  });

  it('links to the items page for every task', async () => {
    server.use(http.get('/api/v1/tasks', () => ok({ items: [{ ...task, status: 'active' }], page: 1, pageSize: 20, total: 1 })));
    renderPage();
    const link = await screen.findByRole('link', { name: '条目' });
    expect(link).toHaveAttribute('href', '/tasks/task-9/items');
  });
});
