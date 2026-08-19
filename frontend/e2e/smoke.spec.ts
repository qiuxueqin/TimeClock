import { test, expect } from '@playwright/test';

test('应用壳在桌面与移动视口无溢出', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByRole('heading', { name: '学习打卡系统' })).toBeVisible();
  // 检查横向溢出：body 宽度不超过视口
  const overflow = await page.evaluate(
    () => document.documentElement.scrollWidth > window.innerWidth,
  );
  expect(overflow).toBe(false);
});
