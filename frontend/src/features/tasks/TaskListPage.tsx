import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, Button, Empty, Modal, Pagination, Progress, Select, Tag, message } from 'antd';
import { Link, useNavigate } from 'react-router-dom';
import { taskApi, type TaskStatus, type TaskView } from '@/api/client';
import { useState } from 'react';
import styles from './TaskListPage.module.css';

function confirmDelete(name: string, onOk: () => Promise<unknown>) {
  Modal.confirm({
    title: '删除任务',
    content: `确定永久删除“${name}”吗？此操作无法恢复。`,
    okText: '删除',
    okButtonProps: { danger: true },
    cancelText: '取消',
    onOk,
  });
}

function TaskCard({ task, activatePending, onActivate, onDelete }: {
  task: TaskView;
  activatePending: boolean;
  onActivate: (id: string) => void;
  onDelete: (task: TaskView) => void;
}) {
  const percent = task.itemCount === 0 ? 0 : Math.round((task.completedItemCount / task.itemCount) * 100);
  return (
    <article className={`${styles.card} tc-card`} data-testid="task-card">
      <div className={styles.cardHead}>
        <Link to={`/tasks/${task.id}/edit`} className={styles.taskNameLink}>{task.name}</Link>
        <Tag color={task.status === 'active' ? 'green' : 'default'}>{task.status === 'active' ? '已启用' : '草稿'}</Tag>
      </div>
      <div className={styles.metaRow}>
        <span>每日 {task.dailyTargetCount} 项</span>
        <span>条目 {task.completedItemCount}/{task.itemCount}</span>
      </div>
      <div>
        <div className={styles.progressLabel}>
          <span>总进度</span>
          <span>{percent}%</span>
        </div>
        <Progress
          percent={percent}
          showInfo={false}
          size="small"
          strokeColor={{ from: 'var(--brand-sky)', to: 'var(--brand-purple)' }}
          aria-label={`任务进度-${task.name}`}
        />
      </div>
      <div className={styles.actions}>
        {task.status === 'draft'
          ? <Button type="primary" size="small" autoInsertSpace={false} loading={activatePending} onClick={() => onActivate(task.id)}>启用</Button>
          : <Link to={`/tasks/${task.id}/today`}>今日</Link>}
        <span style={{ display: 'inline-flex', gap: 12 }}>
          <Link to={`/tasks/${task.id}/items`}>条目</Link>
          <Link to={`/tasks/${task.id}/edit`}>编辑</Link>
          <Button danger type="link" size="small" style={{ padding: 0 }}
            onClick={() => onDelete(task)}>删除</Button>
        </span>
      </div>
    </article>
  );
}

export function TaskListPage() {
  const [page, setPage] = useState(1);
  const [status, setStatus] = useState<TaskStatus | undefined>();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const query = useQuery({ queryKey: ['tasks', { page, pageSize: 20, status }], queryFn: () => taskApi.list({ page, pageSize: 20, status }) });
  const remove = useMutation({ mutationFn: taskApi.remove, onSuccess: async () => { await queryClient.invalidateQueries({ queryKey: ['tasks'] }); message.success('任务已删除'); }, onError: (error: Error) => message.error(error.message) });
  const activate = useMutation({ mutationFn: taskApi.activate, onSuccess: async () => { await queryClient.invalidateQueries({ queryKey: ['tasks'] }); message.success('任务已启用'); }, onError: (error: Error) => message.error(error.message) });

  if (query.isLoading) return <main><p>加载中…</p></main>;
  if (query.isError) return <main><Alert type="error" message={query.error.message} action={<Button onClick={() => query.refetch()}>重试</Button>} /></main>;
  const data = query.data;
  if (!data) return null;
  return (
    <main>
      <div className={styles.header}>
        <h1 className={styles.title}>任务管理</h1>
        <Button type="primary" onClick={() => navigate('/tasks/new')}>创建任务</Button>
      </div>
      <div className={styles.filterRow}>
        <Select allowClear placeholder="筛选状态" value={status}
          onChange={(value) => { setStatus(value); setPage(1); }}
          options={[{ value: 'draft', label: '草稿' }, { value: 'active', label: '已启用' }]}
          style={{ width: 140 }} />
      </div>
      {data.items.length === 0 && (
        <Empty description="还没有任务" style={{ marginTop: 48 }}>
          <Button type="primary" onClick={() => navigate('/tasks/new')}>创建第一个任务</Button>
        </Empty>
      )}
      {data.items.length > 0 && (
        <>
          <div className={styles.grid}>
            {data.items.map((task) => (
              <TaskCard
                key={task.id}
                task={task}
                activatePending={activate.isPending && activate.variables === task.id}
                onActivate={(id) => activate.mutate(id)}
                onDelete={(t) => confirmDelete(t.name, () => remove.mutateAsync(t.id))}
              />
            ))}
          </div>
          {data.total > data.pageSize && (
            <Pagination current={data.page} pageSize={data.pageSize} total={data.total} onChange={setPage} style={{ marginTop: 20 }} />
          )}
        </>
      )}
    </main>
  );
}
