import React, { useEffect, useState } from 'react';
import { Navigate, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { Alert, Button, Form, Input, Typography } from 'antd';
import { Link } from 'react-router-dom';
import { z } from 'zod';
import { ApiError, authApi, getCsrf, type UserView } from '@/api/client';

const credentialsSchema = z.object({ email: z.string().email('请输入有效邮箱'), password: z.string().min(8, '密码至少 8 位') });

type AuthState = { status: 'loading' } | { status: 'anonymous' } | { status: 'authenticated'; user: UserView };

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [state, setState] = useState<AuthState>({ status: 'loading' });
  useEffect(() => {
    void getCsrf().catch(() => undefined).then(() => authApi.me()
      .then((user) => setState({ status: 'authenticated', user }))
      .catch((error: unknown) => {
        if (error instanceof ApiError && error.status === 401) setState({ status: 'anonymous' });
        else setState({ status: 'anonymous' });
      }));
  }, []);
  return <AuthContext.Provider value={{ state, setState }}>{children}</AuthContext.Provider>;
}

const AuthContext = React.createContext<{ state: AuthState; setState: React.Dispatch<React.SetStateAction<AuthState>> } | null>(null);

function useAuth() {
  const value = React.useContext(AuthContext);
  if (!value) throw new Error('AuthProvider missing');
  return value;
}

function AuthForm({ mode }: { mode: 'login' | 'register' }) {
  const { setState } = useAuth();
  const navigate = useNavigate();
  const [error, setError] = useState<string>();
  const [form] = Form.useForm();
  const submit = async (values: Record<string, string>) => {
    setError(undefined);
    const parsed = credentialsSchema.safeParse(values);
    if (!parsed.success) { setError(parsed.error.issues[0]?.message); return; }
    if (mode === 'register' && values.password !== values.confirmPassword) { setError('两次输入的密码不一致'); return; }
    try {
      const result = mode === 'login' ? await authApi.login(parsed.data) : await authApi.register({ ...parsed.data, confirmPassword: values.confirmPassword });
      setState({ status: 'authenticated', user: result.user });
      navigate('/today', { replace: true });
    } catch (e) { setError(e instanceof Error ? e.message : '请求失败'); }
  };
  return <main style={{ maxWidth: 420, margin: '4rem auto' }}><Typography.Title level={2}>{mode === 'login' ? '登录' : '注册'}</Typography.Title>{error && <Alert role="alert" message={error} type="error" />}<Form form={form} layout="vertical" onFinish={submit}><Form.Item label="邮箱" name="email" required><Input autoComplete="email" /></Form.Item><Form.Item label="密码" name="password" required><Input.Password autoComplete={mode === 'login' ? 'current-password' : 'new-password'} /></Form.Item>{mode === 'register' && <Form.Item label="确认密码" name="confirmPassword" required><Input.Password autoComplete="new-password" /></Form.Item>}<Button type="primary" htmlType="submit">{mode === 'login' ? '登录' : '注册'}</Button><div style={{ marginTop: 16 }}>{mode === 'login' ? <>还没有账号？<Link to="/register">去注册</Link></> : <>已有账号？<Link to="/login">去登录</Link></>}</div></Form></main>;
}

export function LoginPage() { return <AuthForm mode="login" />; }
export function RegisterPage() { return <AuthForm mode="register" />; }

export function ProtectedRoute() {
  const { state } = useAuth();
  const location = useLocation();
  if (state.status === 'loading') return <p>加载中…</p>;
  return state.status === 'authenticated' ? <Outlet /> : <Navigate to="/login" replace state={{ from: location }} />;
}

export function AuthenticatedHome() {
  const { state, setState } = useAuth();
  const navigate = useNavigate();
  return <main><Typography.Title>今日学习</Typography.Title><p>{state.status === 'authenticated' ? state.user.email : ''}</p><Button onClick={() => authApi.logout().finally(() => { setState({ status: 'anonymous' }); navigate('/login', { replace: true }); })}>登出</Button></main>;
}
