import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ConfigProvider, App as AntApp } from 'antd';
import { BrowserRouter } from 'react-router-dom';
import { AppRoutes } from '@/app/routes';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
});

/**
 * 应用根组件（S0-FE-01 骨架）。
 * 组合 Ant Design 主题、TanStack Query 与 React Router。
 * 后续 S1-FE-01 在此挂载统一 API 客户端与会话恢复。
 */
export function App() {
  return (
    <ConfigProvider>
      <AntApp>
        <QueryClientProvider client={queryClient}>
          <BrowserRouter>
            <AppRoutes />
          </BrowserRouter>
        </QueryClientProvider>
      </AntApp>
    </ConfigProvider>
  );
}
