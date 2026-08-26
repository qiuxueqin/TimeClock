import { Button } from 'antd';
import { CalendarOutlined, HomeOutlined, MoonFilled, SunFilled, UnorderedListOutlined } from '@ant-design/icons';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { authApi } from '@/api/client';
import { useAuth } from '@/features/auth/Auth';
import { useTheme } from '@/app/ThemeContext';
import styles from './AppLayout.module.css';

const NAV_ITEMS = [
  { to: '/today', label: '今日', icon: <HomeOutlined /> },
  { to: '/calendar', label: '日历', icon: <CalendarOutlined /> },
  { to: '/tasks', label: '任务', icon: <UnorderedListOutlined /> },
];

function ThemeToggle() {
  const { mode, toggle } = useTheme();
  return (
    <Button
      type="text"
      size="small"
      className={styles.themeToggle}
      aria-label={mode === 'light' ? '切换到暗色模式' : '切换到亮色模式'}
      icon={mode === 'light' ? <MoonFilled /> : <SunFilled />}
      onClick={toggle}
    />
  );
}

/** 登录后全局壳：桌面侧边栏 / 移动端顶栏+底部标签栏。 */
export function AppLayout() {
  const { state, setState } = useAuth();
  const navigate = useNavigate();
  const email = state.status === 'authenticated' ? state.user.email : '';

  const logout = () => {
    void authApi.logout().finally(() => {
      setState({ status: 'anonymous' });
      navigate('/login', { replace: true });
    });
  };

  return (
    <div className={styles.layout}>
      <aside className={styles.sidebar}>
        <div className={styles.brand}>
          <span className={styles.brandMark} aria-hidden>
            学
          </span>
          <span className={styles.brandName}>学习打卡</span>
        </div>
        <nav className={styles.nav} aria-label="主导航">
          {NAV_ITEMS.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) =>
                `${styles.navItem} ${isActive ? styles.navItemActive : ''}`
              }
            >
              {item.icon}
              <span>{item.label}</span>
            </NavLink>
          ))}
        </nav>
        <div className={styles.sidebarFooter}>
          <ThemeToggle />
          <span className={styles.userEmail} title={email}>{email}</span>
          <Button size="small" onClick={logout}>登出</Button>
        </div>
      </aside>

      <header className={styles.topbar}>
        <div className={styles.brand}>
          <span className={styles.brandMark} aria-hidden>学</span>
          <span className={styles.brandName}>学习打卡</span>
        </div>
        <ThemeToggle />
      </header>

      <main className={styles.content}>
        <div className={styles.topbarSpacer} />
        <div className={styles.contentInner}>
          <Outlet />
        </div>
      </main>

      <nav className={styles.tabbar} aria-label="底部导航">
        {NAV_ITEMS.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            className={({ isActive }) =>
              `${styles.tabItem} ${isActive ? styles.tabItemActive : ''}`
            }
          >
            {item.icon}
            <span>{item.label}</span>
          </NavLink>
        ))}
      </nav>
    </div>
  );
}
