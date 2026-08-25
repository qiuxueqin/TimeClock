import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http } from 'msw';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { type CalendarMonthResponse, type TaskPage, type CheckinView } from '@/api/client';
import { CalendarPage, CHECKIN_STATUS_META } from '@/features/checkins/CalendarPage';
import { error, ok, server } from '@/test/msw';

const tasks: TaskPage = {
  items: [{ id: 't1', name: '刷题任务', description: null, type: 'checklist', status: 'active', startDate: '2026-08-01', endDate: null, scheduleType: 'daily', timezone: 'Asia/Shanghai', dailyTargetCount: 2, ended: false, itemCount: 10, completedItemCount: 4 }],
  page: 1, pageSize: 100, total: 1,
};

/** 覆盖四种事实状态；noPlan 由无事实日派生（详情接口返回）。 */
const august2026: CalendarMonthResponse = {
  month: '2026-08',
  days: [
    { date: '2026-08-01', status: 'completed', plannedCount: 2, completedCount: 2 },
    { date: '2026-08-02', status: 'partial', plannedCount: 2, completedCount: 1 },
    { date: '2026-08-03', status: 'missed', plannedCount: 2, completedCount: 0 },
    { date: '2026-08-04', status: 'makeup', plannedCount: 2, completedCount: 2, makeupReason: '出差漏做' },
  ],
};

const calendarHandler = (days: CalendarDay2[] = august2026.days) =>
  http.get('/api/v1/calendar', ({ request: req }) => {
    const url = new URL(req.url);
    const filter = url.searchParams.get('filter');
    const month = url.searchParams.get('month') ?? '2026-08';
    const filtered = days.filter((d) => !filter || filter === 'all' || d.status === filter);
    return ok<CalendarMonthResponse>({ month, days: filtered });
  });
type CalendarDay2 = CalendarMonthResponse['days'][number];

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={['/calendar']}><CalendarPage /></MemoryRouter>
    </QueryClientProvider>,
  );
}

function dayCell(date: string): HTMLElement | undefined {
  return screen.getAllByTestId('calendar-day').find((el) => el.getAttribute('data-date') === date);
}

function setupUser() {
  return userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
}

/** antd Select 在 jsdom + fake timers 下的可靠交互：mousedown 开启、点击可见选项（title=标签）。 */
async function pickSelect(testId: string, optionTitle: string) {
  // eslint-disable-next-line @typescript-eslint/no-non-null-assertion
  const combo = screen.getByTestId(testId).querySelector('input[role="combobox"]')!;
  const { fireEvent } = await import('@testing-library/react');
  fireEvent.mouseDown(combo);
  const option = await vi.waitFor(() => {
    const el = document.querySelector<HTMLElement>(`.ant-select-dropdown .ant-select-item-option[title="${optionTitle}"]`);
    if (!el) throw new Error(`option ${optionTitle} not rendered yet`);
    return el;
  }, { timeout: 3000 });
  fireEvent.click(option);
}

beforeEach(() => {
  vi.useFakeTimers({ shouldAdvanceTime: true });
  vi.setSystemTime(new Date('2026-08-25T12:00:00'));
});

afterEach(() => {
  vi.useRealTimers();
});

describe('CalendarPage', () => {
  it('renders fact statuses with three-channel encoding (text + color + icon)', async () => {
    server.use(http.get('/api/v1/tasks', () => ok(tasks)), calendarHandler());
    renderPage();
    expect(await screen.findByTestId('calendar-page')).toBeInTheDocument();
    expect(dayCell('2026-08-01')).toHaveAttribute('data-status', 'completed');
    expect(dayCell('2026-08-02')).toHaveAttribute('data-status', 'partial');
    expect(dayCell('2026-08-03')).toHaveAttribute('data-status', 'missed');
    expect(dayCell('2026-08-04')).toHaveAttribute('data-status', 'makeup');
    for (const status of ['completed', 'partial', 'missed', 'makeup'] as const) {
      expect(screen.getAllByText(CHECKIN_STATUS_META[status].label).length).toBeGreaterThan(0);
    }
  });

  it('shows empty state for a month without facts (cross-month switch)', async () => {
    let requestedMonth = '';
    server.use(http.get('/api/v1/tasks', () => ok(tasks)),
      http.get('/api/v1/calendar', ({ request: req }) => {
        requestedMonth = new URL(req.url).searchParams.get('month') ?? '';
        return ok<CalendarMonthResponse>({ month: requestedMonth, days: [] });
      }));
    renderPage();
    await screen.findByTestId('calendar-page');
    expect(await screen.findByText('本月暂无打卡记录')).toBeInTheDocument();
    expect(requestedMonth).toBe('2026-08');

    // 切换上个月：请求 month 变为 2026-07，仍为空。
    const { fireEvent } = await import('@testing-library/react');
    const pickerInput = screen.getByLabelText('选择月份');
    fireEvent.focus(pickerInput);
    fireEvent.input(pickerInput, { target: { value: '2026-07' } });
    fireEvent.keyDown(pickerInput, { key: 'Enter', keyCode: 13 });
    await waitFor(() => expect(requestedMonth).toBe('2026-07'), { timeout: 5000 });
    expect(screen.getByText('本月暂无打卡记录')).toBeInTheDocument();
  });

  it('filters days by status through the filter select', async () => {
    server.use(http.get('/api/v1/tasks', () => ok(tasks)), calendarHandler());
    renderPage();
    await screen.findAllByTestId('calendar-day');
    await pickSelect('calendar-filter', '已完成');
    await waitFor(() => {
      const cells = screen.getAllByTestId('calendar-day').filter((el) => el.getAttribute('data-status') !== 'empty');
      expect(cells).toHaveLength(1);
      expect(cells[0]).toHaveAttribute('data-date', '2026-08-01');
    });
  });

  it('rejects blank reason and submits valid makeup then refreshes cache', async () => {
    let detailCalls = 0;
    let makeupCalls = 0;
    let lastMakeupBody: string | undefined;
    const missedDetail: CheckinView = {
      id: 'c1', taskId: 't1', checkinDate: '2026-08-23', status: 'missed',
      plannedCount: 2, completedCount: 0,
    };
    server.use(
      http.get('/api/v1/tasks', () => ok(tasks)),
      calendarHandler(),
      http.get('/api/v1/tasks/:taskId/checkins/:date', () => {
        detailCalls += 1;
        return ok(missedDetail);
      }),
      http.post('/api/v1/tasks/:taskId/checkins/:date/makeup', async ({ request: req }) => {
        makeupCalls += 1;
        const body = (await req.json()) as { reason?: string };
        lastMakeupBody = body.reason;
        if (!body.reason?.trim()) return error(422, 'MAKEUP_REASON_REQUIRED', '补打原因不能为空');
        return ok({ ...missedDetail, id: null, status: 'makeup', completedCount: 2, makeupReason: body.reason });
      }),
    );
    renderPage();
    await screen.findAllByTestId('calendar-day');
    await pickSelect('calendar-task-select', '刷题任务');
    // 切换任务会改变 queryKey，等重新拉取完成、单元格重新挂载后再点击。
    await waitFor(() => {
      expect(dayCell('2026-08-03')).toBeTruthy();
    });
    // 点击窗口内漏打日（今天 2026-08-25；08-23 在过去 3 天内）打开详情抽屉。
    const user = setupUser();
    await user.click(dayCell('2026-08-03')!);

    expect(await screen.findByText(/确认补打/)).toBeInTheDocument();
    expect(screen.getByText(/不计入连续打卡天数/)).toBeInTheDocument();

    // 空原因提交 → 前端校验拦截，不发请求。
    await user.click(screen.getByRole('button', { name: /确认补打/ }));
    expect(await screen.findByText('补打原因不能为空')).toBeInTheDocument();
    expect(makeupCalls).toBe(0);

    // 有效原因 → 成功提示 + 缓存失效后重新拉取详情/日历。
    await user.type(screen.getByTestId('makeup-reason'), '出差未带电脑');
    await user.click(screen.getByRole('button', { name: /确认补打/ }));
    await waitFor(() => expect(makeupCalls).toBe(1), { timeout: 5000 });
    expect(lastMakeupBody).toBe('出差未带电脑');
    await waitFor(() => expect(detailCalls).toBeGreaterThanOrEqual(2), { timeout: 5000 });
  });

  it('hides the makeup form when the day is already made up and shows immutability notice', async () => {
    const madeUpDetail: CheckinView = {
      id: 'c9', taskId: 't1', checkinDate: '2026-08-04', status: 'makeup',
      plannedCount: 2, completedCount: 2, makeupReason: '出差漏做',
    };
    server.use(http.get('/api/v1/tasks', () => ok(tasks)), calendarHandler(),
      http.get('/api/v1/tasks/:taskId/checkins/:date', () => ok(madeUpDetail)));
    renderPage();
    await screen.findAllByTestId('calendar-day');
    await pickSelect('calendar-task-select', '刷题任务');
    await waitFor(() => {
      expect(dayCell('2026-08-04')).toBeTruthy();
    });
    const user = setupUser();
    await user.click(dayCell('2026-08-04')!);
    expect(await screen.findByText('该日期已补打，记录不可修改或撤销')).toBeInTheDocument();
    expect(screen.getByText(/补打原因：出差漏做/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /确认补打/ })).not.toBeInTheDocument();
  });
});
