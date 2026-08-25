import { useQuery } from '@tanstack/react-query';
import { Alert, Button, Empty, List, Progress, Skeleton, Statistic, Tag, Typography } from 'antd';
import { Link } from 'react-router-dom';
import { dashboardApi, type DashboardStatus, type TodayTask } from '@/api/client';

/** 派生状态的展示文案与颜色：文字 + 颜色共同表达，不只依赖颜色（可访问性）。 */
export const DASHBOARD_STATUS_META: Record<DashboardStatus, { label: string; color: string }> = {
  notStarted: { label: '未开始', color: 'blue' },
  inProgress: { label: '进行中', color: 'orange' },
  completed: { label: '已完成', color: 'green' },
  noPlan: { label: '无计划', color: 'default' },
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
    return <main data-testid="today-loading" style={{ padding: 24, maxWidth: 960, margin: '0 auto' }}>
      <Skeleton active paragraph={{ rows: 6 }} />
    </main>;
  }
  if (query.isError) {
    return <main style={{ padding: 24, maxWidth: 960, margin: '0 auto' }}>
      <Alert type="error" message={query.error.message}
        action={<Button onClick={() => query.refetch()}>重试</Button>} />
    </main>;
  }
  const data = query.data;
  if (!data) return <Empty description="暂无今日数据" style={{ padding: 24 }} />;
  const hasTasks = data.tasks.length > 0;
  const scheduled = data.tasks.filter((entry) => entry.status !== 'noPlan');
  return (
    <main data-testid="today-page" style={{ padding: 24, maxWidth: 960, margin: '0 auto' }}>
      <Typography.Title level={2} style={{ marginBottom: 4 }}>{greeting()}，今天要继续打卡</Typography.Title>
      <Typography.Text type="secondary" data-testid="today-date">{data.date}</Typography.Text>
      <div style={{ marginTop: 8 }}>
        <Link to="/calendar">查看打卡日历</Link>
      </div>
      <div data-testid="today-summary" style={{ display: 'flex', flexWrap: 'wrap', gap: 24, margin: '16px 0 8px' }}>
        <Statistic title="今日任务" value={data.todayCount} suffix="项" />
        <Statistic title="已完成" value={data.completedCount} suffix="项" />
        <Statistic title="待完成" value={data.pendingCount} suffix="项" />
        <Statistic title="当前连续" data-testid="today-streak" value={data.currentStreak} suffix="天" />
        <Statistic title="最长连续" value={data.longestStreak} suffix="天" />
      </div>
      <Progress
        percent={Math.round(data.completionRate * 100)}
        status={scheduled.some((entry) => entry.status !== 'completed') ? 'active' : 'success'}
        aria-label="今日整体完成率"
      />
      {!hasTasks && (
        <Empty description="还没有任务，从创建一个清单任务开始" style={{ marginTop: 32 }}>
          <Link to="/tasks/new"><Button type="primary">创建任务</Button></Link>
        </Empty>
      )}
      {hasTasks && scheduled.length === 0 && <Empty description="今天没有计划任务" style={{ marginTop: 32 }} />}
      {scheduled.length > 0 && (
        <List
          bordered
          header={<Typography.Text strong>今日任务</Typography.Text>}
          dataSource={scheduled}
          renderItem={(entry) => (
            <List.Item
              data-testid="today-task-row"
              actions={[<Link key="open" to={`/tasks/${entry.task.id}/today`}>打开今日条目</Link>]}
            >
              <List.Item.Meta
                title={
                  <span>
                    {entry.task.name}{' '}
                    <Tag color={DASHBOARD_STATUS_META[entry.status].color}>{DASHBOARD_STATUS_META[entry.status].label}</Tag>
                  </span>
                }
                description={taskDescription(entry)}
              />
            </List.Item>
          )}
        />
      )}
    </main>
  );
}
