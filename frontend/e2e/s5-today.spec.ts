import { test, expect } from '@playwright/test';

// S5 全链路 E2E（desktop + mobile 双视口）：今日页聚合 —— 空状态 -> 未开始 -> 进行中 -> 已完成，
// 并断言打卡全程无 /checkins 写请求（DEC-09）且页面无横向溢出。
test('S5 今日页：空态→未开始→进行中→已完成，汇总与连续摘要随打卡刷新', async ({ page }) => {
  test.setTimeout(240000);

  const randomSuffix = Math.random().toString(36).slice(2, 8).padEnd(6, '0');
  const email = `s5-e2e-${Date.now()}-${randomSuffix}@example.com`;
  const password = 'CorrectHorse1!';

  // 监听 /checkins 写请求：清单型自动打卡由服务端同事务完成，前端不允许第二次写。
  const checkinWrites: string[] = [];
  page.on('request', (request) => {
    if (['POST', 'PUT', 'PATCH', 'DELETE'].includes(request.method()) && request.url().includes('/checkins')) {
      checkinWrites.push(`${request.method()} ${request.url()}`);
    }
  });

  // 注册（不建立会话）后经登录页换取 Cookie，落到 /today。
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

  // 新用户空状态：提示创建任务，且当前视口无横向溢出。
  await expect(page.getByText('还没有任务，从创建一个清单任务开始')).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(
    await page.evaluate(() => window.innerWidth),
  );

  // 创建每日目标为 2 的清单任务并录入 2 个条目，随后启用。
  await page.goto('/tasks/new');
  await page.getByLabel('任务名称').fill('S5 今日任务');
  await page.getByLabel('每日目标').fill('2');
  await page.getByRole('button', { name: /创\s*建/ }).click();
  await expect(page).toHaveURL(/\/tasks$/);
  const taskRow = page.locator('li').filter({ hasText: 'S5 今日任务' });
  await expect(taskRow).toHaveCount(1);
  const itemsLink = taskRow.getByRole('link', { name: '条目' });
  const taskId = ((await itemsLink.getAttribute('href')) as string).split('/')[2];
  await itemsLink.click();
  const titleInput = page.getByLabel('新条目标题');
  const addButton = page.getByRole('button', { name: /添\s*加/ });
  for (let i = 1; i <= 2; i++) {
    await titleInput.fill(`题目 ${i}`);
    await addButton.click();
    await expect(page.getByText(`题目 ${i}`)).toBeVisible();
  }
  await page.goto('/tasks');
  await taskRow.getByRole('button', { name: /启\s*用/ }).click();
  await expect(taskRow.getByText('已启用')).toBeVisible();

  // 今日页：任务行显示"未开始"，进度 0/2。
  await page.goto('/today');
  const row = page.getByTestId('today-task-row').filter({ hasText: 'S5 今日任务' });
  await expect(row).toHaveCount(1);
  await expect(row.getByText('未开始')).toBeVisible();
  await expect(row.getByText('今日进度：0/2')).toBeVisible();

  const itemRows = page.locator('ul.ant-list-items > li');
  const completeButton = (index: number) => itemRows.nth(index).getByRole('button', { name: '完成本题' });

  // 完成第一题 -> 今日页变为"进行中" 1/2。
  await row.getByRole('link', { name: '打开今日条目' }).click();
  await expect(page).toHaveURL(new RegExp(`/tasks/${taskId}/today$`));
  await itemRows.first().locator('textarea[placeholder="填写文字题解"]').fill('题解 1：解题思路……');
  await completeButton(0).click();
  await expect(page.getByTestId('today-progress')).toHaveText('今日进度：1/2');

  // 完成第二题（最后一项，服务端自动打卡）-> 今日页"已完成" 2/2 且连续摘要为 1 天。
  await page.goto(`/tasks/${taskId}/today`);
  await itemRows.nth(1).locator('textarea[placeholder="填写文字题解"]').fill('题解 2：解题思路……');
  await completeButton(1).click();
  await expect(page.getByTestId('today-progress')).toHaveText('今日进度：2/2');

  await page.goto('/today');
  await expect(row.getByText('已完成')).toBeVisible();
  await expect(row.getByText('今日进度：2/2 · 连续 1 天')).toBeVisible();
  await expect(page.getByTestId('today-streak')).toHaveText(/当前连续\s*1\s*天/);
  expect(checkinWrites).toEqual([]);
});
