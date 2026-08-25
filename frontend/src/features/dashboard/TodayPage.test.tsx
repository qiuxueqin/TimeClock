import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http } from 'msw';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { type DashboardTodayResponse, type TaskView } from '@/api/client';
import { TodayPage } from '@/features/dashboard/TodayPage';
import { error, ok, server } from '@/test/msw';

function task(id: string, name: string): TaskView {
  return { id, name, description: null, type: 'checklist', status: 'active', startDate: '2026-08-20', endDate: null, scheduleType: 'daily', timezone: 'Asia/Shanghai', dailyTargetCount: 2, ended: false, itemCount: 4, completedItemCount: 1 };
}

/** S5-API-01 冻结契约示例：未开始 / 进行中 / 已完成 / 无计划日四态混合。 */
const mixed: DashboardTodayResponse = {
  date: '2026-08-25',
  todayCount: 3,
  completedCount: 1,
  pendingCount: 2,
  completionRate: 1 / 3,
  tasks: [
    { task: task('t-progress', '进行中任务'), status: 'inProgress', completedCount: 1, plannedCount: 2, currentStreak: 4 },
    { task: task('t-notstarted', '未开始任务'), status: 'notStarted', completedCount: 0, plannedCount: 1, currentStreak: 0 },
    { task: task('t-completed', '已完成任务'), status: 'completed', completedCount: 2, plannedCount: 2, currentStreak: 7 },
    { task: task('t-noplan', '草稿任务'), status: 'noPlan', completedCount: 0, plannedCount: 0, currentStreak: 0 },
  ],
  currentStreak: 7,
  longestStreak: 9,
};

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(<QueryClientProvider client={client}><MemoryRouter initialEntries={['/today']}><TodayPage /></MemoryRouter></QueryClientProvider>);
}

/** 从 antd Statistic 中按标题读取数值（避开与状态 Tag 同名的文本）。 */
function statValue(title: string): string | undefined {
  return screen.getAllByText(title)
    .map((el) => el.closest('.ant-statistic'))
    .find(Boolean)
    ?.querySelector('.ant-statistic-content-value')
    ?.textContent ?? undefined;
}

describe('TodayPage', () => {
  it('renders mixed statuses with summary, per-task tags, streaks and entry links', async () => {
    server.use(http.get('/api/v1/dashboard/today', () => ok(mixed)));
    renderPage();

    expect(await screen.findByTestId('today-date')).toHaveTextContent('2026-08-25');
    expect(screen.getByTestId('today-streak')).toHaveTextContent('7');
    expect(statValue('今日任务')).toBe('3');
    expect(statValue('已完成')).toBe('1');
    expect(statValue('待完成')).toBe('2');
    expect(screen.getByText('33%')).toBeInTheDocument();
    // 标题只断言固定部分：问候语随当前小时变化。
    expect(screen.getByText(/今天要继续打卡/)).toBeInTheDocument();

    expect(screen.getByText('进行中任务')).toBeInTheDocument();
    expect(screen.getByText('进行中')).toBeInTheDocument();
    expect(screen.getByText('未开始')).toBeInTheDocument();
    // 状态 Tag 与汇总 Statistic 标题同时出现"已完成"，用多匹配断言。
    expect(screen.getAllByText('已完成').length).toBeGreaterThan(0);
    expect(screen.getByText('今日进度：1/2 · 连续 4 天')).toBeInTheDocument();
    expect(screen.getByText('今日进度：0/1')).toBeInTheDocument();
    expect(screen.getByText('今日进度：2/2 · 连续 7 天')).toBeInTheDocument();
    // 无计划日任务不进入列表。
    expect(screen.queryByText('草稿任务')).not.toBeInTheDocument();

    const links = screen.getAllByRole('link', { name: '打开今日条目' }).map((link) => link.getAttribute('href'));
    expect(links).toEqual(['/tasks/t-progress/today', '/tasks/t-notstarted/today', '/tasks/t-completed/today']);
  });

  it('shows the empty state with a create-task entry when no tasks exist', async () => {
    const empty: DashboardTodayResponse = { ...mixed, todayCount: 0, completedCount: 0, pendingCount: 0, completionRate: 0, tasks: [], currentStreak: 0, longestStreak: 0 };
    server.use(http.get('/api/v1/dashboard/today', () => ok(empty)));
    renderPage();
    expect(await screen.findByText('还没有任务，从创建一个清单任务开始')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /创\s*建\s*任\s*务/ })).toHaveAttribute('href', '/tasks/new');
  });

  it('shows the no-plan hint when every task is out of schedule today', async () => {
    const noPlanOnly: DashboardTodayResponse = { ...mixed, todayCount: 0, tasks: [mixed.tasks[3]] };
    server.use(http.get('/api/v1/dashboard/today', () => ok(noPlanOnly)));
    renderPage();
    expect(await screen.findByText('今天没有计划任务')).toBeInTheDocument();
    expect(screen.queryByTestId('today-task-row')).not.toBeInTheDocument();
  });

  it('shows the error alert and recovers through the retry button', async () => {
    server.use(http.get('/api/v1/dashboard/today', () => error(500, 'INTERNAL', '服务器繁忙')));
    renderPage();
    expect(await screen.findByText('服务器繁忙')).toBeInTheDocument();
    server.use(http.get('/api/v1/dashboard/today', () => ok(mixed)));
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: /重\s*试/ }));
    expect(await screen.findByTestId('today-page')).toBeInTheDocument();
    expect(screen.getByText('进行中任务')).toBeInTheDocument();
  });
});
