import { Alert, Button, Empty, List, Progress, Statistic, Typography } from 'antd';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { dashboardApi } from '@/api/client';

export function TodayPage() {
  const query = useQuery({ queryKey: ['dashboard', 'today'], queryFn: dashboardApi.today });
  if (query.isLoading) return <main style={{ padding: 24 }}>今日数据加载中…</main>;
  if (query.isError) return <main style={{ padding: 24 }}><Alert type="error" message={query.error.message} action={<Button onClick={() => query.refetch()}>重试</Button>} /></main>;
  const data = query.data;
  if (!data) return <Empty description="暂无今日数据" />;
  return <main style={{ padding: 24, maxWidth: 960, margin: '0 auto' }}>
    <Typography.Title>今日学习</Typography.Title><Typography.Paragraph>{data.date}</Typography.Paragraph>
    <Progress percent={Math.round(data.completionRate * 100)} status={data.pendingCount ? 'active' : 'success'} />
    <div style={{ display: 'flex', gap: 24, margin: '24px 0' }}><Statistic title="今日任务" value={data.todayCount} /><Statistic title="已完成" value={data.completedCount} /><Statistic title="当前连续" value={data.currentStreak} suffix="天" /></div>
    {data.tasks.length === 0 ? <Empty description="今天没有计划任务" /> : <List bordered dataSource={data.tasks} renderItem={(entry) => <List.Item actions={[<Link key="open" to={`/tasks/${entry.task.id}/today`}>打开今日条目</Link>]}><List.Item.Meta title={entry.task.name} description={`${entry.completedCount}/${entry.plannedCount} · ${entry.reminderText ?? entry.status}`} /></List.Item>} />}
  </main>;
}
