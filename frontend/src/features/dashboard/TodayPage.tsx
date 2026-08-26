import { useQuery } from '@tanstack/react-query';
import { Alert, Button, Empty, Progress, Skeleton, Statistic, Tag } from 'antd';
import { RightOutlined } from '@ant-design/icons';
import { Link } from 'react-router-dom';
import { dashboardApi, type DashboardStatus, type TodayTask } from '@/api/client';
import styles from './TodayPage.module.css';

/** 派生状态的展示文案与颜色：文字 + 颜色共同表达，不只依赖颜色（可访问性）。 */
export const DASHBOARD_STATUS_META: Record<DashboardStatus, { label: string; color: string; barColor: string }> = {
  notStarted: { label: '未开始', color: 'blue', barColor: '#5b8ff9' },
  inProgress: { label: '进行中', color: 'orange', barColor: 'var(--status-partial-dot)' },
  completed: { label: '已完成', color: 'green', barColor: 'var(--status-completed-dot)' },
  noPlan: { label: '无计划', color: 'default', barColor: 'var(--status-noplan-dot)' },
};

function greeting(): string {
  const hour = new Date().getHours();
  if (hour < 5) return '凌晨好';
  if (hour < 12) return '早上好';
  if (hour < 14) return '中午好';
  if (hour < 18) return '下午好';
  return '晚上好';
}

function taskDescription(entry: TodayTask): string {
  const streak = entry.currentStreak > 0 ? ` · 连续 ${entry.currentStreak} 天` : '';
  return entry.status === 'noPlan'
    ? `今日无计划${streak}`
    : `今日进度：${entry.completedCount}/${entry.plannedCount}${streak}`;
}

/** S5 今日页：日期、问候、汇总、任务列表与连续摘要；骨架 / 空 / 错误可恢复。 */
export function TodayPage() {
  const query = useQuery({ queryKey: ['dashboard', 'today'], queryFn: dashboardApi.today });
  if (query.isPending) {
    return (
      <main data-testid="today-loading">
        <Skeleton active paragraph={{ rows: 6 }} />
      </main>
    );
  }
  if (query.isError) {
    return (
      <main>
        <Alert type="error" message={query.error.message}
          action={<Button onClick={() => query.refetch()}>重试</Button>} />
      </main>
    );
  }
  const data = query.data;
  if (!data) return <Empty description="暂无今日数据" />;
  const hasTasks = data.tasks.length > 0;
  const scheduled = data.tasks.filter((entry) => entry.status !== 'noPlan');
  const allDone = !scheduled.some((entry) => entry.status !== 'completed');
  return (
    <main data-testid="today-page" className={styles.page}>
      <div className={styles.greeting}>
        <h1 className={styles.greetingTitle}>{greeting()}，今天要继续打卡</h1>
        <div className={styles.dateRow}>
          <span data-testid="today-date">{data.date}</span>
          <Link to="/calendar" className={styles.calendarLink}>查看打卡日历</Link>
        </div>
      </div>

      <div data-testid="today-summary" className={styles.stats}>
        <div className={`${styles.statCard} tc-card`}>
          <Statistic title="今日任务" value={data.todayCount} suffix="项" />
        </div>
        <div className={`${styles.statCard} tc-card`}>
          <Statistic title="已完成" value={data.completedCount} suffix="项" />
        </div>
        <div className={`${styles.statCard} tc-card`}>
          <Statistic title="待完成" value={data.pendingCount} suffix="项" />
        </div>
        <div className={`${styles.statCard} ${styles.statStreak}`}>
          <Statistic title="当前连续" data-testid="today-streak" value={data.currentStreak} suffix="天" />
        </div>
        <div className={`${styles.statCard} tc-card`}>
          <Statistic title="最长连续" value={data.longestStreak} suffix="天" />
        </div>
      </div>

      {scheduled.length > 0 && (
        <section className={`${styles.progressCard} tc-card`}>
          <div className={styles.progressLabel}>
            <span className={styles.progressTitle}>今日整体进度</span>
            <span className={styles.progressValue}>{Math.round(data.completionRate * 100)}%</span>
          </div>
          <Progress
            className={styles.progressBar}
            percent={Math.round(data.completionRate * 100)}
            showInfo={false}
            strokeColor={{ from: 'var(--brand-sky)', to: 'var(--brand-purple)' }}
            status={allDone ? 'success' : 'active'}
            aria-label="今日整体完成率"
          />
        </section>
      )}

      {!hasTasks && (
        <Empty description="还没有任务，从创建一个清单任务开始" style={{ marginTop: 32 }}>
          <Link to="/tasks/new"><Button type="primary">创建任务</Button></Link>
        </Empty>
      )}
      {hasTasks && scheduled.length === 0 && (
        <Empty description="今天没有计划任务" style={{ marginTop: 32 }}>
          <span className={styles.noPlanHint}>
            今天不在任何任务的计划日内。<Link to="/tasks">管理任务</Link>或<Link to="/calendar">查看打卡日历</Link>
          </span>
        </Empty>
      )}
      {scheduled.length > 0 && (
        <section aria-label="今日任务">
          <div className={styles.sectionHeader}>
            <span className={styles.sectionTitle}>今日任务</span>
          </div>
          <div className={styles.taskList}>
            {scheduled.map((entry) => (
              <div key={entry.task.id} data-testid="today-task-row" className={`${styles.taskRow} tc-card`}>
                <span
                  aria-hidden
                  className={styles.statusBar}
                  style={{ background: DASHBOARD_STATUS_META[entry.status].barColor }}
                />
                <div className={styles.taskMain}>
                  <span className={styles.taskName}>{entry.task.name}</span>
                  <Tag color={DASHBOARD_STATUS_META[entry.status].color}>{DASHBOARD_STATUS_META[entry.status].label}</Tag>
                  <div className={styles.taskDesc}>{taskDescription(entry)}</div>
                </div>
                <div className={styles.taskSide}>
                  <Link to={`/tasks/${entry.task.id}/today`} aria-label={`打开今日条目`}>
                    打开今日条目 <RightOutlined style={{ fontSize: 11 }} />
                  </Link>
                </div>
              </div>
            ))}
          </div>
        </section>
      )}
    </main>
  );
}
