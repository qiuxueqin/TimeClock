import { test, expect } from '@playwright/test';

// S4 全链路 E2E：清单型任务"逐项完成 -> 自动打卡（DEC-09，前端不发 /checkins 写请求）-> 刷新持久 -> 撤销回退"。
test('S4 清单完成闭环：空题解阻止→逐项完成自动打卡→刷新持久→撤销回 4/5', async ({ page }) => {
  // 全链路较长（注册 + 建任务 + 启用 + 添加 5 条目 + 5 次完成 + 刷新 + 撤销），放宽单测超时到 240s。
  test.setTimeout(240000);

  // 步骤 1：随机化账号（时间戳 + 随机 6 位），保证可重复执行互不冲突。
  const randomSuffix = Math.random().toString(36).slice(2, 8).padEnd(6, '0');
  const email = `s4-e2e-${Date.now()}-${randomSuffix}@example.com`;
  const password = 'CorrectHorse1!';

  // 步骤 2：监听并收集所有指向 /checkins 的写请求（POST/PUT/PATCH/DELETE）。
  // DEC-09：清单型最后一项达标由服务端同事务自动打卡，前端绝不允许发起第二次打卡写请求。
  const checkinWrites: string[] = [];
  page.on('request', (request) => {
    const method = request.method();
    if (
      ['POST', 'PUT', 'PATCH', 'DELETE'].includes(method) &&
      request.url().includes('/checkins')
    ) {
      checkinWrites.push(`${method} ${request.url()}`);
    }
  });

  // 步骤 3：UI 注册新用户，成功后跳转 /today。
  await page.goto('/register');
  await page.getByLabel('邮箱').fill(email);
  await page.getByRole('textbox', { name: '* 密码' }).fill(password);
  await page.getByRole('textbox', { name: '* 确认密码' }).fill(password);
  // 注意：antd 会给两字中文按钮自动插入空格（"注 册"），因此用正则匹配可访问名称。
  await page.getByRole('button', { name: /注\s*册/ }).click();
  await expect(page).toHaveURL(/\/today$/);

  // 注册接口只创建账号、不建立会话；整页跳转前先通过登录页换取 SESSION_ID Cookie，
  // 否则后续 goto 的全量加载会因无会话被 ProtectedRoute 弹回 /login。
  await page.goto('/login');
  await page.getByLabel('邮箱').fill(email);
  await page.getByRole('textbox', { name: '* 密码' }).fill(password);
  await page.getByRole('button', { name: /登\s*录/ }).click();
  await expect(page).toHaveURL(/\/today$/);

  // 步骤 4：创建清单任务"S4 闭环任务"，每日目标 5，成功后跳转 /tasks。
  await page.goto('/tasks/new');
  await page.getByLabel('任务名称').fill('S4 闭环任务');
  await page.getByLabel('每日目标').fill('5');
  await page.getByRole('button', { name: /创\s*建/ }).click();
  await expect(page).toHaveURL(/\/tasks$/);

  // 步骤 5：定位含任务名的行（先等列表加载且唯一）。DEC-04：至少 1 个已确认条目才能启用，
  // 因此先进入"条目"页录入题目，再回来执行启用。
  const taskRow = page.locator('li').filter({ hasText: 'S4 闭环任务' });
  await expect(taskRow).toHaveCount(1);

  // 步骤 6：从该行"条目"链接 href（形如 /tasks/<taskId>/items）提取 taskId，再点击进入条目页。
  const itemsLink = taskRow.getByRole('link', { name: '条目' });
  const itemsHref = await itemsLink.getAttribute('href');
  expect(itemsHref).toMatch(/^\/tasks\/[^/]+\/items$/);
  const taskId = (itemsHref as string).split('/')[2];
  await itemsLink.click();
  await expect(page).toHaveURL(new RegExp(`/tasks/${taskId}/items$`));

  // 循环添加 5 个条目：填标题 -> 点"添加" -> 页面出现该标题（antd 渲染用 expect 轮询等待）。
  const titleInput = page.getByLabel('新条目标题');
  const addButton = page.getByRole('button', { name: /添\s*加/ });
  for (let i = 1; i <= 5; i++) {
    await titleInput.fill(`题目 ${i}`);
    await addButton.click();
    await expect(page.getByText(`题目 ${i}`)).toBeVisible();
  }

  // 步骤 7：回到任务列表启用任务；行内状态 Tag 变为"已启用"，进度显示 0/5。
  await page.goto('/tasks');
  const activeRow = page.locator('li').filter({ hasText: 'S4 闭环任务' });
  await expect(activeRow).toHaveCount(1);
  await activeRow.getByRole('button', { name: /启\s*用/ }).click();
  await expect(activeRow.getByText('已启用')).toBeVisible();

  // 步骤 8：进入今日页，初始进度为 0/5（直接字符串断言，避免正则转义问题）。
  await page.goto(`/tasks/${taskId}/today`);
  const progress = page.getByTestId('today-progress');
  await expect(progress).toHaveText('今日进度：0/5');

  // 条目行按 sort_order 排列于 ul.ant-list-items 下；缓存行定位器（live query，reload 后仍有效）。
  const rows = page.locator('ul.ant-list-items > li');

  // 步骤 8：空题解时第一个"完成本题"按钮应为 disabled（DEC-08 客户端拦截）。
  await expect(rows.first().getByRole('button', { name: '完成本题' })).toBeDisabled();

  // 步骤 9：逐项填写题解并点"完成本题"；每完成一项，今日进度即时 +1（服务端自动打卡）。
  for (let i = 0; i < 5; i++) {
    const row = rows.nth(i);
    await row.locator('textarea[placeholder="填写文字题解"]').fill(`题解 ${i + 1}：解题思路……`);
    await row.getByRole('button', { name: '完成本题' }).click();
    await expect(progress).toHaveText(`今日进度：${i + 1}/5`);
  }

  // 步骤 10：全程断言没有任何 /checkins 写请求 —— 打卡完全由服务端自动完成。
  expect(checkinWrites).toEqual([]);

  // 步骤 11：刷新后进度仍为 5/5，且页面出现 5 个"已完成"Tag（完成状态已持久化）。
  await page.reload();
  await expect(progress).toHaveText('今日进度：5/5');
  await expect(page.locator('.ant-tag', { hasText: '已完成' })).toHaveCount(5);

  // 步骤 12：撤销第一项 -> 进度回到 4/5；题解草稿保留（值仍匹配 /题解 1/）；依旧无任何 /checkins 写请求。
  await rows.first().getByRole('button', { name: '撤销完成' }).click();
  await expect(progress).toHaveText('今日进度：4/5');
  await expect(rows.first().locator('textarea[placeholder="填写文字题解"]')).toHaveValue(/题解 1/);
  expect(checkinWrites).toEqual([]);
});
