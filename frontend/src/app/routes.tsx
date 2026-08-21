import { Routes, Route, Navigate } from 'react-router-dom';
import { Home } from '@/app/Home';
import { LoginPage, ProtectedRoute, RegisterPage } from '@/features/auth/Auth';
import { TaskFormPage } from '@/features/tasks/TaskFormPage';
import { TaskListPage } from '@/features/tasks/TaskListPage';
import { ItemPage } from '@/features/items/ItemPage';
import { ImportPage } from '@/features/imports/ImportPage';
import { TodayPage } from '@/features/dashboard/TodayPage';

export function AppRoutes() {
  return <Routes>
    <Route path="/login" element={<LoginPage />} />
    <Route path="/register" element={<RegisterPage />} />
    <Route element={<ProtectedRoute />}>
      <Route path="/" element={<Home />} />
      <Route path="/today" element={<TodayPage />} />
      <Route path="/tasks" element={<TaskListPage />} />
      <Route path="/tasks/new" element={<TaskFormPage />} />
      <Route path="/tasks/:taskId/edit" element={<TaskFormPage />} />
      <Route path="/tasks/:taskId/items" element={<ItemPage />} />
      <Route path="/tasks/:taskId/today" element={<ItemPage />} />
      <Route path="/tasks/:taskId/import" element={<ImportPage />} />
    </Route>
    <Route path="*" element={<Navigate to="/today" replace />} />
  </Routes>;
}
