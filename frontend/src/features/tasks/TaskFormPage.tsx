import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, Button, DatePicker, Form, Input, InputNumber } from 'antd';
import { ArrowLeftOutlined, CalendarOutlined, ProfileOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { taskApi, type TaskCreateRequest } from '@/api/client';
import { taskFormSchema } from './taskFormSchema';
import { useEffect, useState } from 'react';
import styles from './TaskFormPage.module.css';

export function TaskFormPage() {
  const { taskId } = useParams(); const editing = Boolean(taskId); const navigate = useNavigate(); const queryClient = useQueryClient();
  const [form] = Form.useForm(); const [error, setError] = useState<string>();
  const detail = useQuery({ queryKey: ['task', taskId], queryFn: () => taskApi.get(taskId!), enabled: editing });
  const mutation = useMutation({ mutationFn: (body: TaskCreateRequest) => editing ? taskApi.update(taskId!, body) : taskApi.create(body), onSuccess: async () => { await queryClient.invalidateQueries({ queryKey: ['tasks'] }); if (taskId) await queryClient.invalidateQueries({ queryKey: ['task', taskId] }); navigate('/tasks'); }, onError: (e: unknown) => setError(e instanceof Error ? e.message : '请求失败') });
  useEffect(() => { if (detail.data) form.setFieldsValue({ ...detail.data, startDate: dayjs(detail.data.startDate), endDate: detail.data.endDate ? dayjs(detail.data.endDate) : undefined }); }, [detail.data, form]);
  const submit = (values: Record<string, unknown>) => { setError(undefined); const normalized = { ...values, startDate: (values.startDate as dayjs.Dayjs).format('YYYY-MM-DD'), endDate: values.endDate ? (values.endDate as dayjs.Dayjs).format('YYYY-MM-DD') : '' }; const parsed = taskFormSchema.safeParse(normalized); if (!parsed.success) { setError(parsed.error.issues[0]?.message ?? '表单校验失败'); return; } mutation.mutate({ ...parsed.data, type: 'checklist', scheduleType: 'daily', endDate: parsed.data.endDate || undefined }); };
  if (editing && detail.isLoading) return <main className={styles.page}><p>加载中…</p></main>;
  if (editing && detail.isError) return <main className={styles.page}><Alert type="error" message={detail.error.message} /><Button onClick={() => navigate('/tasks')}>返回任务列表</Button></main>;
  return <main className={styles.page}>
    <div className={styles.header}>
      <Link to="/tasks" className={styles.backLink} aria-label="返回任务列表">
        <ArrowLeftOutlined /> 任务列表
      </Link>
      <h1 className={styles.title}>{editing ? '编辑任务' : '创建任务'}</h1>
      <p className={styles.subtitle}>清单型任务：每天按目标完成题目，自动打卡记录连续天数</p>
    </div>
    {error && <Alert role="alert" type="error" message={error} style={{ marginBottom: 16 }} />}
    <Form form={form} layout="vertical" onFinish={submit} initialValues={{ startDate: dayjs(), timezone: 'Asia/Shanghai', dailyTargetCount: 1 }}>
      <section className={`${styles.section} tc-card`}>
        <div className={styles.sectionTitle}><span className={styles.sectionIcon} aria-hidden><ProfileOutlined /></span>基本信息</div>
        <Form.Item label="任务名称" name="name" required><Input placeholder="例如：高数刷题" /></Form.Item>
        <Form.Item label="任务描述" name="description"><Input.TextArea rows={3} placeholder="补充说明（可选）" /></Form.Item>
      </section>
      <section className={`${styles.section} tc-card`}>
        <div className={styles.sectionTitle}><span className={styles.sectionIcon} aria-hidden><CalendarOutlined /></span>计划规则</div>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0 16px' }}>
          <Form.Item label="开始日期" name="startDate" required><DatePicker style={{ width: '100%' }} /></Form.Item>
          <Form.Item label="结束日期" name="endDate"><DatePicker style={{ width: '100%' }} /></Form.Item>
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0 16px' }}>
          <Form.Item label="每日目标" name="dailyTargetCount" required><InputNumber min={1} precision={0} style={{ width: '100%' }} /></Form.Item>
          <Form.Item label="任务时区" name="timezone" required extra="计划日与打卡结算均按此时区计算">
            <Input />
          </Form.Item>
        </div>
      </section>
      <div className={styles.footer}>
        <Button className={styles.submitBtn} type="primary" htmlType="submit" loading={mutation.isPending}>{editing ? '保存' : '创建'}</Button>
        <Button onClick={() => navigate('/tasks')}>取消</Button>
        {!editing && <span className={styles.footerHint}>创建后可在任务卡片中录入条目并启用</span>}
      </div>
    </Form>
  </main>;
}
