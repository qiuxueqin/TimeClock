import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ConfigProvider, App as AntApp, theme } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { BrowserRouter } from 'react-router-dom';
import { AuthProvider } from '@/features/auth/Auth';
import { ThemeProvider, useTheme } from '@/app/ThemeContext';
import { AppRoutes } from '@/app/routes';
import { darkTheme, lightTheme } from '@/styles/theme';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
});

function ThemedConfig({ children }: { children: React.ReactNode }) {
  const { mode } = useTheme();
  const themeConfig = mode === 'dark'
    ? { ...darkTheme, algorithm: theme.darkAlgorithm }
    : { ...lightTheme, algorithm: theme.defaultAlgorithm };
  return (
    <ConfigProvider locale={zhCN} theme={themeConfig} button={{ autoInsertSpace: false }}>
      <AntApp>{children}</AntApp>
    </ConfigProvider>
  );
}

/**
 * 应用根组件。
 * 组合主题（亮/暗）、Ant Design 5 主题、TanStack Query 与 React Router。
 */
export function App() {
  return (
    <ThemeProvider>
      <ThemedConfig>
        <QueryClientProvider client={queryClient}>
          <BrowserRouter>
            <AuthProvider>
              <AppRoutes />
            </AuthProvider>
          </BrowserRouter>
        </QueryClientProvider>
      </ThemedConfig>
    </ThemeProvider>
  );
}
