import { test, expect } from '@playwright/test';

const email = `e2e-${Date.now()}@example.com`;
const password = 'CorrectHorse1!';

test('注册页可见且未认证访问受保护页会跳转登录', async ({ page }) => {
  await page.goto('/today');
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByRole('heading', { name: '登录' })).toBeVisible();
});

test('两个浏览器上下文 Cookie 隔离且可登录恢复', async ({ browser }) => {
  const first = await browser.newContext();
  const second = await browser.newContext();
  try {
    const pageA = await first.newPage();
    const pageB = await second.newPage();
    await pageA.goto('/register');
    await pageA.getByLabel('邮箱').fill(email);
    await pageA.getByRole('textbox', { name: '* 密码' }).fill(password);
    await pageA.getByRole('textbox', { name: '* 确认密码' }).fill(password);
    await pageA.getByRole('button', { name: '注册' }).click();
    await expect(pageA).toHaveURL(/\/today$/);
    await pageA.reload();
    await expect(pageA.getByText(email)).toBeVisible();
    expect(await first.cookies()).not.toEqual(await second.cookies());
    await pageB.goto('/today');
    await expect(pageB).toHaveURL(/\/login$/);
  } finally {
    await first.close();
    await second.close();
  }
});
