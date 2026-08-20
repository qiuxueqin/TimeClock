import { Routes, Route, Navigate } from 'react-router-dom';
import { Home } from '@/app/Home';
import { AuthenticatedHome, LoginPage, ProtectedRoute, RegisterPage } from '@/features/auth/Auth';
import { TaskFormPage } from '@/features/tasks/TaskFormPage';
import { TaskListPage } from '@/features/tasks/TaskListPage';

export function AppRoutes() {
  return <Routes>
    <Route path="/login" element={<LoginPage />} />
    <Route path="/register" element={<RegisterPage />} />
    <Route element={<ProtectedRoute />}>
      <Route path="/" element={<Home />} />
      <Route path="/today" element={<AuthenticatedHome />} />
      <Route path="/tasks" element={<TaskListPage />} />
      <Route path="/tasks/new" element={<TaskFormPage />} />
      <Route path="/tasks/:taskId/edit" element={<TaskFormPage />} />
    </Route>
    <Route path="*" element={<Navigate to="/today" replace />} />
  </Routes>;
}
