import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, Button, Empty, List, Modal, Pagination, Select, Space, Tag, Typography, message } from 'antd';
import { Link, useNavigate } from 'react-router-dom';
import { taskApi, type TaskStatus } from '@/api/client';
import { useState } from 'react';

export function TaskListPage() {
  const [page, setPage] = useState(1);
  const [status, setStatus] = useState<TaskStatus | undefined>();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const query = useQuery({ queryKey: ['tasks', { page, pageSize: 20, status }], queryFn: () => taskApi.list({ page, pageSize: 20, status }) });
  const remove = useMutation({ mutationFn: taskApi.remove, onSuccess: async () => { await queryClient.invalidateQueries({ queryKey: ['tasks'] }); message.success('任务已删除'); }, onError: (error: Error) => message.error(error.message) });
  const activate = useMutation({ mutationFn: taskApi.activate, onSuccess: async () => { await queryClient.invalidateQueries({ queryKey: ['tasks'] }); message.success('任务已启用'); }, onError: (error: Error) => message.error(error.message) });
  if (query.isLoading) return <main style={{ padding: 24 }}><Typography.Title level={2}>任务管理</Typography.Title><p>加载中…</p></main>;
  if (query.isError) return <main style={{ padding: 24 }}><Alert type="error" message={query.error.message} action={<Button onClick={() => query.refetch()}>重试</Button>} /></main>;
  const data = query.data;
  if (!data) return null;
  return <main style={{ padding: 24, maxWidth: 960, margin: '0 auto' }}>
    <Space style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}><Typography.Title level={2} style={{ margin: 0 }}>任务管理</Typography.Title><Button type="primary" onClick={() => navigate('/tasks/new')}>创建任务</Button></Space>
    <Select allowClear placeholder="筛选状态" value={status} onChange={(value) => { setStatus(value); setPage(1); }} options={[{ value: 'draft', label: '草稿' }, { value: 'active', label: '已启用' }]} style={{ width: 140, marginBottom: 16 }} />
    {data.items.length === 0 ? <Empty description="还没有任务" /> : <List bordered dataSource={data.items} renderItem={(task) => <List.Item actions={[...(task.status === 'draft' ? [<Button key="activate" type="link" loading={activate.isPending && activate.variables === task.id} onClick={() => activate.mutate(task.id)}>启用</Button>] : []), <Link key="items" to={`/tasks/${task.id}/items`}>条目</Link>, <Link key="edit" to={`/tasks/${task.id}/edit`}>编辑</Link>, <Button key="delete" danger type="link" onClick={() => Modal.confirm({ title: '删除任务', content: `确定永久删除“${task.name}”吗？此操作无法恢复。`, okText: '删除', cancelText: '取消', onOk: () => remove.mutateAsync(task.id) })}>删除</Button>]}> <List.Item.Meta title={<Link to={`/tasks/${task.id}/edit`}>{task.name}</Link>} description={<Space wrap><Tag color={task.status === 'active' ? 'green' : 'default'}>{task.status === 'active' ? '已启用' : '草稿'}</Tag><span>进度：{task.completedItemCount}/{task.itemCount}</span><span>每日 {task.dailyTargetCount} 项</span></Space>} /></List.Item>} />}
    {data.total > data.pageSize && <Pagination current={data.page} pageSize={data.pageSize} total={data.total} onChange={setPage} style={{ marginTop: 16 }} />}
  </main>;
}
