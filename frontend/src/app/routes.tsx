import { Routes, Route, Navigate } from 'react-router-dom';
import { Home } from '@/app/Home';
import { AuthenticatedHome, LoginPage, ProtectedRoute, RegisterPage } from '@/features/auth/Auth';

export function AppRoutes() {
  return <Routes>
    <Route path="/login" element={<LoginPage />} />
    <Route path="/register" element={<RegisterPage />} />
    <Route element={<ProtectedRoute />}>
      <Route path="/" element={<Home />} />
      <Route path="/today" element={<AuthenticatedHome />} />
    </Route>
    <Route path="*" element={<Navigate to="/today" replace />} />
  </Routes>;
}
