import { test, expect } from '@playwright/test';

test('应用壳在桌面与移动视口无溢出', async ({ page }) => {
  // 未认证访问根路径会被 ProtectedRoute 重定向到登录页（S1 行为）。
  await page.goto('/');
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByRole('heading', { name: /登\s*录/ })).toBeVisible();
  // 检查横向溢出：body 宽度不超过视口
  const overflow = await page.evaluate(
    () => document.documentElement.scrollWidth > window.innerWidth,
  );
  expect(overflow).toBe(false);
});
