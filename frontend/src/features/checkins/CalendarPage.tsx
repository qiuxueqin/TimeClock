import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert, Button, DatePicker, Drawer, Empty, Form, Input, Select, Skeleton, Spin, Tag, Typography, message,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { checkinApi, taskApi, type CalendarDay, type CheckinView } from '@/api/client';
import styles from './CalendarPage.module.css';

/** 状态展示：文字 + 颜色共同表达（可访问性），图标作为第三通道。 */
export const CHECKIN_STATUS_META: Record<CalendarDay['status'], { label: string; color: string; icon: string }> = {
  completed: { label: '已完成', color: 'green', icon: '✓' },
  partial: { label: '部分完成', color: 'orange', icon: '◐' },
  missed: { label: '已漏打', color: 'red', icon: '✗' },
  makeup: { label: '已补打', color: 'purple', icon: '↺' },
  noPlan: { label: '无计划', color: 'default', icon: '-' },
};

const FILTER_OPTIONS = [
  { value: 'all', label: '全部状态' },
  { value: 'completed', label: '已完成' },
  { value: 'partial', label: '部分完成' },
  { value: 'missed', label: '已漏打' },
  { value: 'makeup', label: '已补打' },
];

/** 补打资格由后端裁决；前端仅对窗口内 missed/partial 提供入口，最终以提交结果为准。 */
function isMakeupCandidate(day: Pick<CalendarDay, 'date' | 'status'>, today: Dayjs): boolean {
  if (day.status !== 'missed' && day.status !== 'partial') return false;
  const d = dayjs(day.date);
  return d.isBefore(today, 'day') && d.isAfter(today.subtract(4, 'day'));
}

export function CalendarPage() {
  const queryClient = useQueryClient();
  const [month, setMonth] = useState(() => dayjs().format('YYYY-MM'));
  const [filter, setFilter] = useState<string>('all');
  const [selectedTaskId, setSelectedTaskId] = useState<string | undefined>();
  const [detailDate, setDetailDate] = useState<string | null>(null);
  const [form] = Form.useForm<{ reason: string }>();

  const query = useQuery({
    queryKey: ['calendar', month, selectedTaskId ?? null, filter],
    queryFn: () => checkinApi.calendar(month, { taskId: selectedTaskId, filter: filter as never }),
  });
  const detail = useQuery({
    queryKey: ['checkin-detail', selectedTaskId ?? null, detailDate],
    queryFn: () => checkinApi.detail(selectedTaskId!, detailDate!),
    enabled: Boolean(selectedTaskId && detailDate),
  });

  const makeup = useMutation({
    mutationFn: (vars: { taskId: string; date: string; reason: string }) =>
      checkinApi.makeup(vars.taskId, vars.date, vars.reason.trim()),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['calendar'] });
      await queryClient.invalidateQueries({ queryKey: ['checkin-detail'] });
      await queryClient.invalidateQueries({ queryKey: ['dashboard', 'today'] });
      message.success('补打成功。补打计入完成率但不计入连续打卡');
      form.resetFields();
    },
    onError: (error: Error) => message.error(error.message),
  });

  const today = dayjs();
  const tasksQuery = useQuery({ queryKey: ['tasks', { scope: 'calendar' }], queryFn: () => taskApi.list({ pageSize: 100 }) });
  const taskOptions = [
    { value: undefined as string | undefined, label: '全部任务（合并视图）' },
    ...(tasksQuery.data?.items ?? []).map((t) => ({ value: t.id, label: t.name })),
  ];
  const days = query.data?.days ?? [];
  const daysByDate = new Map(days.map((d) => [d.date, d]));
  const firstDay = dayjs(`${month}-01`);
  const leadingBlanks = firstDay.day(); // 周日=0
  const cellCount = Math.ceil((leadingBlanks + firstDay.daysInMonth()) / 7) * 7;

  const submitMakeup = async () => {
    if (!selectedTaskId || !detailDate) return;
    const values = await form.validateFields();
    makeup.mutate({ taskId: selectedTaskId, date: detailDate, reason: values.reason });
  };

  const detailData: CheckinView | undefined = detail.data;
  const canMakeup =
    Boolean(detailData && selectedTaskId && detailDate &&
      (detailData.status === 'missed' || detailData.status === 'partial') &&
      isMakeupCandidate({ date: detailData.checkinDate, status: detailData.status }, today));

  return (
    <main data-testid="calendar-page" className={styles.page}>
      <Typography.Title level={2} style={{ marginBottom: 16 }}>打卡日历</Typography.Title>
      <div className={`${styles.toolbar} tc-card`}>
        <DatePicker picker="month" value={dayjs(month)} allowClear={false}
          onChange={(value) => value && setMonth(value.format('YYYY-MM'))} aria-label="选择月份" />
        <Select value={selectedTaskId ?? undefined} onChange={(value) => setSelectedTaskId(value)}
          options={taskOptions} style={{ width: 200 }} aria-label="选择任务" data-testid="calendar-task-select"
          placeholder="全部任务（合并视图）" allowClear={false} />
        <Select value={filter} onChange={setFilter} options={FILTER_OPTIONS} style={{ width: 130 }}
          aria-label="筛选状态" data-testid="calendar-filter" />
      </div>
      {query.isPending && <Skeleton active paragraph={{ rows: 5 }} />}
      {query.isError && (
        <Alert type="error" message={query.error.message}
          action={<Button onClick={() => query.refetch()}>重试</Button>} />
      )}
      {query.data && days.length === 0 && (
        <Empty description="本月暂无打卡记录" style={{ marginTop: 32 }} />
      )}
      {days.length > 0 && (
        <section className={`${styles.calendarCard} tc-card`} aria-label="月历网格">
          <div className={styles.weekRow} aria-hidden>
            {['日', '一', '二', '三', '四', '五', '六'].map((w) => (
              <span key={w} className={styles.weekday}>{w}</span>
            ))}
          </div>
          <div className={styles.grid}>
            {Array.from({ length: leadingBlanks }).map((_, i) => <span key={`b${i}`} />)}
            {Array.from({ length: cellCount - leadingBlanks }, (_, i) => firstDay.date(i + 1)).map((d) => {
              const key = d.format('YYYY-MM-DD');
              const day = daysByDate.get(key);
              const meta = CHECKIN_STATUS_META[day?.status ?? 'noPlan'];
              const isToday = d.isSame(today, 'day');
              return (
                <button
                  key={key}
                  type="button"
                  data-testid="calendar-day"
                  data-date={key}
                  data-status={day?.status ?? 'empty'}
                  title={day ? `${key} ${meta.label}` : key}
                  onClick={() => { setDetailDate(key); }}
                  className={[
                    styles.dayCell,
                    day ? styles[`status-${day.status}`] : '',
                    isToday ? styles.today : '',
                  ].filter(Boolean).join(' ')}
                >
                  <span className={styles.dayNumber}>{d.date()}</span>
                  {day && (
                    <span className={styles.dayStatus}>
                      <span className={`${styles.dot} ${styles[`dot-${day.status}`]}`} aria-hidden />
                      <span aria-hidden>{meta.icon}</span>
                      <Tag color={meta.color} style={{ marginInlineEnd: 0, fontSize: 10, lineHeight: '16px', padding: '0 4px', borderRadius: 4 }}>
                        {meta.label}
                      </Tag>
                    </span>
                  )}
                </button>
              );
            })}
          </div>
          <div className={styles.legend} aria-label="状态图例">
            {(['completed', 'partial', 'missed', 'makeup'] as const).map((status) => (
              <span key={status} className={styles.legendItem}>
                <span className={`${styles.legendSwatch} ${styles[`swatch-${status}`]}`}>
                  <span className={`${styles.dot} ${styles[`dot-${status}`]}`} style={{ position: 'absolute', top: -2, right: -2 }} aria-hidden />
                </span>
                {CHECKIN_STATUS_META[status].label}
              </span>
            ))}
          </div>
        </section>
      )}
      <Drawer
        title={detailDate ? `${detailDate} 打卡详情` : ''}
        open={Boolean(detailDate)}
        onClose={() => { setDetailDate(null); form.resetFields(); }}
        width={400}
      >
        {!selectedTaskId && (
          <Alert
            type="info" showIcon
            message="当前为全部任务合并视图"
            description="合并视图仅展示每日汇总状态。选择上方具体任务，或前往任务列表，即可查看某天的完成进度、题解摘要与补打入口。"
            action={<Button size="small"><Link to="/tasks">去任务列表</Link></Button>}
          />
        )}
        {selectedTaskId && detail.isPending && <Spin />}
        {selectedTaskId && detail.isError && (
          <Alert type="warning" message={(detail.error as Error).message}
            action={<Button onClick={() => detail.refetch()}>重试</Button>} />
        )}
        {selectedTaskId && detailData && (
          <>
            <div className={styles.detailSection}>
              <div className={styles.detailLabel}>状态</div>
              <div className={styles.detailValue}>
                <Tag color={CHECKIN_STATUS_META[detailData.status].color}>
                  {CHECKIN_STATUS_META[detailData.status].icon} {CHECKIN_STATUS_META[detailData.status].label}
                </Tag>
              </div>
            </div>
            {detailData.plannedCount != null && (
              <div className={styles.detailSection}>
                <div className={styles.detailLabel}>进度</div>
                <div className={styles.detailValue}>{detailData.completedCount ?? 0}/{detailData.plannedCount}</div>
              </div>
            )}
            {detailData.makeupReason && (
              <div className={styles.detailSection}>
                <blockquote className={styles.reasonBlock}>补打原因：{detailData.makeupReason}</blockquote>
              </div>
            )}
            {detailData.solutionSummary && (
              <div className={styles.detailSection}>
                <div className={styles.detailLabel}>题解摘要</div>
                <Typography.Paragraph>{detailData.solutionSummary}</Typography.Paragraph>
              </div>
            )}
            {(detailData.status === 'makeup') && (
              <Alert type="info" message="该日期已补打，记录不可修改或撤销" style={{ marginTop: 12 }} />
            )}
            {canMakeup && (
              <Form form={form} layout="vertical" style={{ marginTop: 20 }} onFinish={submitMakeup}>
                <Alert
                  type="warning" showIcon style={{ marginBottom: 12 }}
                  message="补打计入完成率，但不计入连续打卡天数，也无法撤销"
                />
                <Form.Item name="reason" label="补打原因" required
                  rules={[
                    { required: true, message: '补打原因不能为空' },
                    { whitespace: true, message: '补打原因不能为空' },
                    { max: 500, message: '补打原因最多 500 字符' },
                  ]}
                >
                  <Input.TextArea rows={3} maxLength={500} placeholder="例如：出差未带电脑" data-testid="makeup-reason" />
                </Form.Item>
                <Button type="primary" htmlType="submit" loading={makeup.isPending} danger block>
                  确认补打
                </Button>
              </Form>
            )}
          </>
        )}
      </Drawer>
    </main>
  );
}
