import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, Button, DatePicker, Form, Input, InputNumber, Typography } from 'antd';
import dayjs from 'dayjs';
import { useNavigate, useParams } from 'react-router-dom';
import { taskApi, type TaskCreateRequest } from '@/api/client';
import { taskFormSchema } from './taskFormSchema';
import { useEffect, useState } from 'react';

export function TaskFormPage() {
  const { taskId } = useParams(); const editing = Boolean(taskId); const navigate = useNavigate(); const queryClient = useQueryClient();
  const [form] = Form.useForm(); const [error, setError] = useState<string>();
  const detail = useQuery({ queryKey: ['task', taskId], queryFn: () => taskApi.get(taskId!), enabled: editing });
  const mutation = useMutation({ mutationFn: (body: TaskCreateRequest) => editing ? taskApi.update(taskId!, body) : taskApi.create(body), onSuccess: async () => { await queryClient.invalidateQueries({ queryKey: ['tasks'] }); if (taskId) await queryClient.invalidateQueries({ queryKey: ['task', taskId] }); navigate('/tasks'); }, onError: (e: unknown) => setError(e instanceof Error ? e.message : '请求失败') });
  useEffect(() => { if (detail.data) form.setFieldsValue({ ...detail.data, startDate: dayjs(detail.data.startDate), endDate: detail.data.endDate ? dayjs(detail.data.endDate) : undefined }); }, [detail.data, form]);
  const submit = (values: Record<string, unknown>) => { setError(undefined); const normalized = { ...values, startDate: (values.startDate as dayjs.Dayjs).format('YYYY-MM-DD'), endDate: values.endDate ? (values.endDate as dayjs.Dayjs).format('YYYY-MM-DD') : '' }; const parsed = taskFormSchema.safeParse(normalized); if (!parsed.success) { setError(parsed.error.issues[0]?.message ?? '表单校验失败'); return; } mutation.mutate({ ...parsed.data, type: 'checklist', scheduleType: 'daily', endDate: parsed.data.endDate || undefined }); };
  if (editing && detail.isLoading) return <main style={{ padding: 24 }}><p>加载中…</p></main>;
  if (editing && detail.isError) return <main style={{ padding: 24 }}><Alert type="error" message={detail.error.message} /><Button onClick={() => navigate('/tasks')}>返回任务列表</Button></main>;
  return <main style={{ padding: 24, maxWidth: 640, margin: '0 auto' }}><Typography.Title level={2}>{editing ? '编辑任务' : '创建任务'}</Typography.Title>{error && <Alert role="alert" type="error" message={error} style={{ marginBottom: 16 }} />}<Form form={form} layout="vertical" onFinish={submit} initialValues={{ startDate: dayjs(), timezone: 'Asia/Shanghai', dailyTargetCount: 1 }}><Form.Item label="任务名称" name="name" required><Input /></Form.Item><Form.Item label="任务描述" name="description"><Input.TextArea rows={4} /></Form.Item><Form.Item label="开始日期" name="startDate" required><DatePicker /></Form.Item><Form.Item label="结束日期" name="endDate"><DatePicker /></Form.Item><Form.Item label="任务时区" name="timezone" required><Input /></Form.Item><Form.Item label="每日目标" name="dailyTargetCount" required><InputNumber min={1} precision={0} /></Form.Item><Button type="primary" htmlType="submit" loading={mutation.isPending}>{editing ? '保存' : '创建'}</Button> <Button onClick={() => navigate('/tasks')}>取消</Button></Form></main>;
}
