import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, Button, Input, Progress, Tag, message } from 'antd';
import { ArrowLeftOutlined, CheckCircleFilled } from '@ant-design/icons';
import { Link, useLocation, useParams } from 'react-router-dom';
import { useState } from 'react';
import { itemApi, submissionApi, type ItemView } from '@/api/client';
import styles from './ItemPage.module.css';

function ItemCard({ item, solution, onSolutionChange }: {
  item: ItemView;
  solution: Record<string, string>;
  onSolutionChange: (id: string, value: string) => void;
}) {
  const queryClient = useQueryClient();
  const { taskId } = useParams<{ taskId: string }>();
  const complete = useMutation({ mutationFn: ({ id, text }: { id: string; text: string }) => submissionApi.complete(id, text), onSuccess: async (res) => { await queryClient.invalidateQueries({ queryKey: ['items', taskId] }); await queryClient.invalidateQueries({ queryKey: ['today-items', taskId] }); await queryClient.invalidateQueries({ queryKey: ['dashboard', 'today'] }); message.success(res.checkinStatus === 'completed' ? '已完成，今日打卡达成' : '已完成'); }, onError: (e: Error) => message.error(e.message) });
  const reopen = useMutation({ mutationFn: (id: string) => submissionApi.reopen(id), onSuccess: async () => { await queryClient.invalidateQueries({ queryKey: ['items', taskId] }); await queryClient.invalidateQueries({ queryKey: ['today-items', taskId] }); await queryClient.invalidateQueries({ queryKey: ['dashboard', 'today'] }); message.success('已撤销完成'); }, onError: (e: Error) => message.error(e.message) });
  const done = item.status === 'completed';
  return (
    <article className={`${styles.itemCard} tc-card ${done ? styles.itemCompleted : ''}`}>
      <div className={styles.itemHead}>
        <span className={`${styles.sortBadge} ${done ? styles.sortBadgeDone : ''}`} aria-hidden>
          {done ? <CheckCircleFilled /> : item.sortOrder}
        </span>
        <span className={styles.itemTitle}>{item.title}</span>
        {done && <Tag color="green">已完成</Tag>}
      </div>
      {item.content && <div className={styles.itemContent}>{item.content}</div>}
      {item.analysis && <div className={styles.analysis}>参考解析：{item.analysis}</div>}
      <div className={styles.solutionArea}>
        <Input.TextArea
          aria-label={`题解-${item.id}`}
          value={solution[item.id] ?? item.solutionText ?? ''}
          onChange={(event) => onSolutionChange(item.id, event.target.value)}
          placeholder="填写文字题解"
          rows={3}
        />
        <div className={styles.solutionActions}>
          {done
            ? <Button size="small" loading={reopen.isPending} disabled={reopen.isPending} onClick={() => reopen.mutate(item.id)}>撤销完成</Button>
            : <Button type="primary" size="small" loading={complete.isPending}
                disabled={complete.isPending || !(solution[item.id] ?? item.solutionText ?? '').trim()}
                onClick={() => complete.mutate({ id: item.id, text: solution[item.id] ?? item.solutionText ?? '' })}>完成本题</Button>}
        </div>
      </div>
    </article>
  );
}

export function ItemPage() {
  const { taskId } = useParams<{ taskId: string }>();
  const location = useLocation();
  const isToday = location.pathname.endsWith('/today');
  const queryClient = useQueryClient();
  const [title, setTitle] = useState('');
  const [paste, setPaste] = useState('');
  const query = useQuery({ queryKey: ['items', taskId], queryFn: () => itemApi.list(taskId!), enabled: Boolean(taskId) });
  const today = useQuery({ queryKey: ['today-items', taskId], queryFn: () => itemApi.today(taskId!), enabled: Boolean(taskId) && isToday });
  const create = useMutation({ mutationFn: () => itemApi.create(taskId!, { title: title.trim() }), onSuccess: async () => { setTitle(''); await queryClient.invalidateQueries({ queryKey: ['items', taskId] }); message.success('条目已添加'); }, onError: (e: Error) => message.error(e.message) });
  const preview = useMutation({ mutationFn: () => itemApi.pastePreview(taskId!, paste), onError: (e: Error) => message.error(e.message) });
  const confirm = useMutation({ mutationFn: () => itemApi.pasteConfirm(taskId!, preview.data?.candidates ?? []), onSuccess: async () => { setPaste(''); preview.reset(); await queryClient.invalidateQueries({ queryKey: ['items', taskId] }); message.success('粘贴条目已导入'); }, onError: (e: Error) => message.error(e.message) });
  const [solution, setSolution] = useState<Record<string, string>>({});

  if (query.isLoading) return <main><p>条目加载中…</p></main>;
  if (query.isError) return <main><Alert type="error" message={query.error.message} action={<Button onClick={() => query.refetch()}>重试</Button>} /></main>;

  const items = query.data?.items ?? [];
  const percent = today.data && today.data.plannedCount > 0
    ? Math.round((today.data.completedCount / today.data.plannedCount) * 100)
    : 0;

  return (
    <main className={styles.page}>
      <div className={styles.header}>
        <div className={styles.headerLeft}>
          <Link to="/tasks" className={styles.backLink} aria-label="返回任务列表">
            <ArrowLeftOutlined /> 任务列表
          </Link>
          <h1 className={styles.title}>{isToday ? '今日条目' : '学习条目'}</h1>
        </div>
        <span className={styles.headerActions}>
          {!isToday && <Link to={`/tasks/${taskId}/today`}>今日条目</Link>}
          {!isToday && <Link to={`/tasks/${taskId}/import`}>导入文件</Link>}
          {isToday && <Link to={`/tasks/${taskId}/items`}>全部条目</Link>}
        </span>
      </div>

      {isToday && today.data && (
        <section className={`${styles.todayBanner} tc-card`} data-testid="today-banner">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline' }}>
            <span className={styles.todayBannerLabel} data-testid="today-progress">今日进度：{today.data.completedCount}/{today.data.plannedCount}</span>
            <span style={{ fontSize: 13, color: 'var(--text-secondary)' }}>{percent}%</span>
          </div>
          <Progress
            percent={percent}
            showInfo={false}
            strokeColor={{ from: 'var(--brand-sky)', to: 'var(--brand-purple)' }}
            status={percent >= 100 ? 'success' : 'active'}
            aria-label="今日进度条"
          />
        </section>
      )}
      {/* 非 /today 路由不渲染横幅；保留原 testid 挂载点语义（仅 isToday 时存在） */}

      <section className={`${styles.addRow} tc-card`}>
        <Input.Group compact style={{ display: 'flex' }}>
          <Input
            aria-label="新条目标题"
            value={title}
            onChange={(event) => setTitle(event.target.value)}
            placeholder="输入条目标题"
            onPressEnter={() => title.trim() && create.mutate()}
            style={{ flex: 1 }}
          />
          <Button type="primary" loading={create.isPending} disabled={!title.trim()} onClick={() => create.mutate()}>添加</Button>
        </Input.Group>
      </section>

      <section className={styles.section}>
        <div className={styles.sectionHead}>
          <span className={styles.sectionTitle}>粘贴导入</span>
          <span style={{ fontSize: 12, color: 'var(--text-tertiary)' }}>每行一个条目，预览确认后入库；批量题目可用 <Link to={`/tasks/${taskId}/import`}>xlsx 导入</Link></span>
        </div>
        <div className={`${styles.sectionBody} tc-card`} style={{ padding: '14px 16px' }}>
          <Input.TextArea aria-label="粘贴条目" rows={4} value={paste} onChange={(event) => setPaste(event.target.value)} placeholder={'每行一个条目'} />
          <div style={{ marginTop: 10, display: 'flex', gap: 8 }}>
            <Button disabled={!paste.trim()} loading={preview.isPending} onClick={() => preview.mutate()}>预览</Button>
            {preview.data && <Button type="primary" loading={confirm.isPending} disabled={!preview.data.candidates.length} onClick={() => confirm.mutate()}>确认导入（{preview.data.validLines}）</Button>}
          </div>
          {preview.data && (
            <Alert style={{ marginTop: 10 }} type={preview.data.errorLines.length ? 'warning' : 'info'}
              message={`共 ${preview.data.totalLines} 行，有效 ${preview.data.validLines} 行，错误 ${preview.data.errorLines.length} 行`}
              description={preview.data.errorLines.map((error) => `第${error.lineNumber}行：${error.reason}`).join('；')} />
          )}
        </div>
      </section>

      <section aria-label="条目列表">
        <div className={styles.itemList}>
          {items.length === 0 && <Alert type="info" showIcon message="暂无条目" />}
          {items.map((item) => (
            <ItemCard key={item.id} item={item} solution={solution} onSolutionChange={(id, value) => setSolution((old) => ({ ...old, [id]: value }))} />
          ))}
        </div>
      </section>
    </main>
  );
}
