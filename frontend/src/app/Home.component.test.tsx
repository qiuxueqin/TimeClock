import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { Home } from '@/app/Home';

// S0-QA-01 前端组件测试示例（TEST-S0-QA-01-01 的一部分：验证组件测试可被发现与执行）。
// 这是"预期通过的示例测试"；S0-OPS-02 将验证 CI 在失败时阻断。
describe('Home 应用壳（组件测试层）', () => {
  it('渲染标题', () => {
    render(<Home />);
    expect(
      screen.getByRole('heading', { name: '学习打卡系统' }),
    ).toBeInTheDocument();
  });
});
