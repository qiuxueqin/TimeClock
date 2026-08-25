import { execSync } from 'node:child_process';
import { test, expect } from '@playwright/test';

// S6 全链路 E2E（desktop + mobile 双视口）：TEST-S6-QA-01-01 历史与补打闭环 ——
// 连续完成 3 个计划日 -> 下一计划日漏打 -> 补打该日 -> 再完成新计划日；
// 校验今日、详情、日历、统计四处一致；补打不修复连续链（不变量 7/8）。
//
// 漏打历史无法纯由 UI 制造（结算只对已录入条目的日期产生事实），因此：
// 1) UI 创建任务（startDate=今天-4、目标 2）并录入 10 题、启用；
// 2) 用 SQL 将 created_at 回拨到任务开始日，使前 3 天成为计划日，
//    并播种前 3 天 completed 事实与条目完成时间（昨天保持无事实）；
// 3) 打开日历页时服务端对 active 任务自动执行漏打结算 -> 昨天形成 missed。

const MYSQL = 'D:/DevTools/mysql-8.0.39/bin/mysql';
const DB_ARGS = ['-h', process.env.TC_MYSQL_HOST ?? '47.99.78.232', '-P', '3306',
  '-u', process.env.TC_MYSQL_USERNAME ?? 'root', `-p${process.env.TC_MYSQL_PASSWORD}`, 'time_clock'];

function sql(statement: string): void {
  execSync(`"${MYSQL}" ${DB_ARGS.map((a) => JSON.stringify(a)).join(' ')} --binary-mode`,
    { input: statement, stdio: ['pipe', 'ignore', 'pipe'] });
}

test('S6 日历补打闭环：3 计划日完成→漏打→补打→新计划日完成，四处一致且补打不接续连续链', async ({ page }) => {
  test.setTimeout(300000);

  const randomSuffix = Math.random().toString(36).slice(2, 8).padEnd(6, '0');
  const email = `s6-e2e-${Date.now()}-${randomSuffix}@example.com`;
  const password = 'CorrectHorse1!';

  // 注册（不建立会话）后经登录页换取 Cookie。
  await page.goto('/register');
  await page.getByLabel('邮箱').fill(email);
  await page.getByRole('textbox', { name: '* 密码' }).fill(password);
  await page.getByRole('textbox', { name: '* 确认密码' }).fill(password);
  await page.getByRole('button', { name: /注\s*册/ }).click();
  await expect(page).toHaveURL(/\/today$/);
  await page.goto('/login');
  await page.getByLabel('邮箱').fill(email);
  await page.getByRole('textbox', { name: '* 密码' }).fill(password);
  await page.getByRole('button', { name: /登\s*录/ }).click();
  await expect(page).toHaveURL(/\/today$/);

  // 创建目标 2 的清单任务：开始日期=今天-4，使前 3 天成为计划日。
  const startDate = new Date();
  startDate.setDate(startDate.getDate() - 4);
  const startISO = startDate.toISOString().slice(0, 10);
  await page.goto('/tasks/new');
  await page.getByLabel('任务名称').fill('S6 补打任务');
  await page.getByLabel('开始日期').fill(startISO);
  await page.getByLabel('每日目标').fill('2');
  await page.getByRole('button', { name: /创\s*建/ }).click();
  await expect(page).toHaveURL(/\/tasks$/);
  const taskRow = page.locator('li').filter({ hasText: 'S6 补打任务' });
  await expect(taskRow).toHaveCount(1);
  const itemsLink = taskRow.getByRole('link', { name: '条目' });
  const taskId = ((await itemsLink.getAttribute('href')) as string).split('/')[2];
  await itemsLink.click();
  const titleInput = page.getByLabel('新条目标题');
  const addButton = page.getByRole('button', { name: /添\s*加/ });
  for (let i = 1; i <= 10; i++) {
    await titleInput.fill(`题目 ${i}`);
    await addButton.click();
    await expect(page.getByText(`题目 ${i}`)).toBeVisible();
  }

  // 启用任务。
  await page.goto('/tasks');
  await taskRow.getByRole('button', { name: /启\s*用/ }).click();
  await expect(taskRow.getByText('已启用')).toBeVisible();

  // 回填历史：created_at 提前到开始日 00:30；播种开始日起连续 3 天 completed
  // （每天 2 题完成）。昨天（第 4 个计划日）不播种任何事实，
  // 等待日历读取时的漏打结算产生 missed。
  const dayISO = (n: number) => {
    const d = new Date(startISO);
    d.setDate(d.getDate() + n);
    return d.toISOString().slice(0, 10);
  };
  sql(`
    UPDATE learning_items SET created_at = CONCAT('${startISO}', ' 00:30:00') WHERE task_id='${taskId}';
    INSERT INTO checkins (id,task_id,checkin_date,status,planned_count,completed_count,makeup_reason,created_at,updated_at) VALUES
      (UUID(),'${taskId}','${dayISO(0)}','completed',2,2,NULL,CONCAT('${dayISO(0)}',' 21:00:00'),CONCAT('${dayISO(0)}',' 21:00:00')),
      (UUID(),'${taskId}','${dayISO(1)}','completed',2,2,NULL,CONCAT('${dayISO(1)}',' 21:00:00'),CONCAT('${dayISO(1)}',' 21:00:00')),
      (UUID(),'${taskId}','${dayISO(2)}','completed',2,2,NULL,CONCAT('${dayISO(2)}',' 21:00:00'),CONCAT('${dayISO(2)}',' 21:00:00'));
    UPDATE learning_items SET status='completed', solution_text='历史题解', completed_at=CONCAT('${dayISO(0)}',' 21:00:00')
      WHERE task_id='${taskId}' AND sort_order<=2;
    UPDATE learning_items SET status='completed', solution_text='历史题解', completed_at=CONCAT('${dayISO(1)}',' 21:00:00')
      WHERE task_id='${taskId}' AND sort_order IN (3,4);
    UPDATE learning_items SET status='completed', solution_text='历史题解', completed_at=CONCAT('${dayISO(2)}',' 21:00:00')
      WHERE task_id='${taskId}' AND sort_order IN (5,6);
  `);

  // 打开日历：合并视图应显示 4 个事实日 —— 前 3 天已完成 + 昨天 missed（读取时结算）。
  await page.goto('/calendar');
  const cell = (date: string) => page.locator(`button[data-testid="calendar-day"][data-date="${date}"]`);
  await expect(cell(dayISO(0))).toHaveAttribute('data-status', 'completed');
  await expect(cell(dayISO(1))).toHaveAttribute('data-status', 'completed');
  await expect(cell(dayISO(2))).toHaveAttribute('data-status', 'completed');
  await expect(cell(dayOffsetISO(1))).toHaveAttribute('data-status', 'missed');

  // 选择具体任务（补打入口需要任务上下文），打开昨天详情并补打。
  const makeupDate = dayOffsetISO(1);
  await page.getByTestId('calendar-task-select').click();
  await page.locator('.ant-select-dropdown .ant-select-item-option[title="S6 补打任务"]').click();
  await cell(makeupDate).click();
  await expect(page.getByText(`${makeupDate} 打卡详情`)).toBeVisible();
  await expect(page.getByText(/进度：0\/2/)).toBeVisible();

  // 空原因被前端拦截。
  await page.getByRole('button', { name: /确\s*认\s*补\s*打/ }).click();
  await expect(page.getByText('补打原因不能为空')).toBeVisible();

  // 有效原因补打成功：状态变 makeup、显示原因与不可撤销提示。
  await page.getByTestId('makeup-reason').fill('出差未带电脑');
  await page.getByRole('button', { name: /确\s*认\s*补\s*打/ }).click();
  await expect(page.getByText('该日期已补打，记录不可修改或撤销')).toBeVisible();
  await expect(page.getByText(/补打原因：出差未带电脑/)).toBeVisible();
  await expect(cell(makeupDate)).toHaveAttribute('data-status', 'makeup');

  // 详情接口（同一浏览器会话）返回 makeup 与原因 —— 四处一致之一：详情。
  const detailRes = await page.request.get(`/api/v1/tasks/${taskId}/checkins/${makeupDate}`);
  expect(detailRes.ok()).toBeTruthy();
  const detail = (await detailRes.json()).data;
  expect(detail.status).toBe('makeup');
  expect(detail.makeupReason).toBe('出差未带电脑');

  // 统计接口：makeup 不计也不修复连续链 —— 昨日 missed 断开播种的 3 天连续，
  // 当前连续为 0（今天尚未完成不计数也不断链）；最长连续为播种的 3 天；
  // 条目进度 6 完成/共 10。
  const statsRes = await page.request.get(`/api/v1/tasks/${taskId}/stats`);
  expect(statsRes.ok()).toBeTruthy();
  const stats = (await statsRes.json()).data;
  expect(stats.currentStreak).toBe(0);
  expect(stats.longestStreak).toBe(3);
  expect(stats.completedItemCount).toBe(6);
  expect(stats.totalItemCount).toBe(10);

  // 再完成新计划日（今天）：完成今日 2 题（题目 7/8），服务端自动打卡（DEC-09）。
  // 列表前 6 条是播种的历史已完成条目，需按标题定位今日 pending 条目。
  await page.goto(`/tasks/${taskId}/today`);
  const itemRow = (title: string) =>
    page.locator('ul.ant-list-items > li').filter({ hasText: title });
  const completeItem = async (title: string, solutionText: string) => {
    const row = itemRow(title);
    await row.locator('textarea[placeholder="填写文字题解"]').fill(solutionText);
    await row.getByRole('button', { name: '完成本题' }).click();
  };
  await completeItem('题目 7', '今日题解 7');
  await expect(page.getByTestId('today-progress')).toHaveText('今日进度：1/2');
  await completeItem('题目 8', '今日题解 8');
  await expect(page.getByTestId('today-progress')).toHaveText('今日进度：2/2');

  // 今日页：已完成，但当前连续仅 1 天（昨日 missed 断链，补打不修复，
  // 今天的完成重新起算）。
  await page.goto('/today');
  const todayRow = page.getByTestId('today-task-row').filter({ hasText: 'S6 补打任务' });
  await expect(todayRow.getByText('已完成')).toBeVisible();
  await expect(todayRow.getByText(/今日进度：2\/2 · 连续 1 天/)).toBeVisible();

  // 今日接口一致：completed 且当前连续 1。
  const todayRes = await page.request.get('/api/v1/dashboard/today');
  expect(todayRes.ok()).toBeTruthy();
  const todayData = (await todayRes.json()).data;
  const todayEntry = todayData.tasks.find((t: { task: { id: string } }) => t.task.id === taskId);
  expect(todayEntry.status).toBe('completed');
  expect(todayEntry.currentStreak).toBe(1);

  // 刷新日历：昨天 makeup 保持，今天 completed 出现；日历与统计一致。
  await page.goto('/calendar');
  await expect(cell(makeupDate)).toHaveAttribute('data-status', 'makeup');
  const todayISO = new Date().toISOString().slice(0, 10);
  await expect(cell(todayISO)).toHaveAttribute('data-status', 'completed');
  const statsRes2 = await page.request.get(`/api/v1/tasks/${taskId}/stats`);
  const stats2 = (await statsRes2.json()).data;
  expect(stats2.currentStreak).toBe(1);
  expect(stats2.longestStreak).toBe(3);
  expect(stats2.completedItemCount).toBe(8);
});

/** 今天往前 offset 天的本地 ISO 日期（与任务时区 Asia/Shanghai 一致的机器上运行）。 */
function dayOffsetISO(offset: number): string {
  const d = new Date();
  d.setDate(d.getDate() - offset);
  return d.toISOString().slice(0, 10);
}
