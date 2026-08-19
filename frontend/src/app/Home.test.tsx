import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { Home } from '@/app/Home';

// S0-FE-01 前端冒烟测试（TEST-S0-FE-01-01）：应用壳可渲染且包含关键文案。
describe('Home 应用壳', () => {
  it('渲染应用标题与描述', () => {
    render(
      <MemoryRouter>
        <Home />
      </MemoryRouter>,
    );
    expect(
      screen.getByRole('heading', { name: '学习打卡系统' }),
    ).toBeInTheDocument();
    expect(screen.getByText(/前端工程骨架已就绪/)).toBeInTheDocument();
  });
});
