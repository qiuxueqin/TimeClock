import { Routes, Route } from 'react-router-dom';
import { Home } from '@/app/Home';

/**
 * 应用路由表（S0-FE-01 骨架）。
 * 所有者：前端 Agent（路由表为单 owner 共享文件）。
 * 完整路由见 frontend-tech-stack-V1.0.md §4；后续阶段逐步接入受保护路由。
 */
export function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<Home />} />
    </Routes>
  );
}
