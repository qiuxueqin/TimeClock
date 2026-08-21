import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, Button, Divider, Input, List, Space, Tag, Typography, message } from 'antd';
import { Link, useParams } from 'react-router-dom';
import { useState } from 'react';
import { itemApi } from '@/api/client';

export function ItemPage() {
  const { taskId } = useParams<{ taskId: string }>();
  const queryClient = useQueryClient();
  const [title, setTitle] = useState('');
  const [paste, setPaste] = useState('');
  const query = useQuery({ queryKey: ['items', taskId], queryFn: () => itemApi.list(taskId!), enabled: Boolean(taskId) });
  const create = useMutation({ mutationFn: () => itemApi.create(taskId!, { title: title.trim() }), onSuccess: async () => { setTitle(''); await queryClient.invalidateQueries({ queryKey: ['items', taskId] }); message.success('条目已添加'); }, onError: (e: Error) => message.error(e.message) });
  const preview = useMutation({ mutationFn: () => itemApi.pastePreview(taskId!, paste), onError: (e: Error) => message.error(e.message) });
  const confirm = useMutation({ mutationFn: () => itemApi.pasteConfirm(taskId!, preview.data?.candidates ?? []), onSuccess: async () => { setPaste(''); preview.reset(); await queryClient.invalidateQueries({ queryKey: ['items', taskId] }); message.success('粘贴条目已导入'); }, onError: (e: Error) => message.error(e.message) });
  if (query.isLoading) return <main style={{ padding: 24 }}>条目加载中…</main>;
  if (query.isError) return <main style={{ padding: 24 }}><Alert type="error" message={query.error.message} action={<Button onClick={() => query.refetch()}>重试</Button>} /></main>;
  return <main style={{ padding: 24, maxWidth: 900, margin: '0 auto' }}>
    <Space style={{ display: 'flex', justifyContent: 'space-between' }}><Typography.Title level={2}>学习条目</Typography.Title><Link to={`/tasks/${taskId}/import`}>导入文件</Link></Space>
    <Space.Compact style={{ width: '100%', marginBottom: 16 }}><Input aria-label="新条目标题" value={title} onChange={(event) => setTitle(event.target.value)} placeholder="输入条目标题" onPressEnter={() => title.trim() && create.mutate()} /><Button type="primary" loading={create.isPending} disabled={!title.trim()} onClick={() => create.mutate()}>添加</Button></Space.Compact>
    <Typography.Title level={4}>粘贴导入</Typography.Title>
    <Input.TextArea aria-label="粘贴条目" rows={4} value={paste} onChange={(event) => setPaste(event.target.value)} placeholder="每行一个条目" />
    <Space style={{ margin: '8px 0 16px' }}><Button disabled={!paste.trim()} loading={preview.isPending} onClick={() => preview.mutate()}>预览</Button>{preview.data && <Button type="primary" loading={confirm.isPending} disabled={!preview.data.candidates.length} onClick={() => confirm.mutate()}>确认导入（{preview.data.validLines}）</Button>}</Space>
    {preview.data && <Alert type={preview.data.errorLines.length ? 'warning' : 'info'} message={`共 ${preview.data.totalLines} 行，有效 ${preview.data.validLines} 行，错误 ${preview.data.errorLines.length} 行`} description={preview.data.errorLines.map((error) => `第${error.lineNumber}行：${error.reason}`).join('；')} />}
    <Divider />
    <List bordered dataSource={query.data?.items ?? []} locale={{ emptyText: '暂无条目' }} renderItem={(item) => <List.Item><List.Item.Meta title={<span>{item.sortOrder}. {item.title} {item.status === 'completed' && <Tag color="green">已完成</Tag>}</span>} description={item.content || '无正文'} /></List.Item>} />
  </main>;
}
