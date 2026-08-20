import { z } from 'zod';

export const taskFormSchema = z.object({
  name: z.string().trim().min(1, '任务名称不能为空').max(50, '任务名称最多 50 个字符'),
  description: z.string().max(500, '任务描述最多 500 个字符').optional(),
  startDate: z.string().regex(/^\d{4}-\d{2}-\d{2}$/, '请输入有效开始日期'),
  endDate: z.string().regex(/^\d{4}-\d{2}-\d{2}$/, '请输入有效结束日期').optional().or(z.literal('')),
  timezone: z.string().trim().min(1, '时区不能为空'),
  dailyTargetCount: z.coerce.number().int('每日目标必须是整数').min(1, '每日目标至少为 1'),
}).refine((value) => !value.endDate || value.endDate >= value.startDate, {
  path: ['endDate'], message: '结束日期不能早于开始日期',
});

export type TaskFormValues = z.infer<typeof taskFormSchema>;
