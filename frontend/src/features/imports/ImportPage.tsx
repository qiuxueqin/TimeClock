import * as React from 'react';
import { Alert, Button, Empty, List, Typography, Upload, message } from 'antd';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useParams } from 'react-router-dom';
import { importApi } from '@/api/client';

export function ImportPage() {
  const { taskId } = useParams<{ taskId: string }>();
  const client = useQueryClient();
  const [preview, setPreview] = React.useState<Awaited<ReturnType<typeof importApi.xlsxPreview>>>();
  const confirm = useMutation({
    mutationFn: () => importApi.xlsxConfirm(taskId!, preview!.candidates.map((candidate) => ({ ...candidate, action: candidate.duplicate ? 'skip' : 'keep_new' })) ),
    onSuccess: async () => { message.success('导入已确认'); setPreview(undefined); await client.invalidateQueries({ queryKey: ['task-items', taskId] }); await client.invalidateQueries({ queryKey: ['task', taskId] }); },
    onError: (error: Error) => message.error(error.message),
  });
  return <main style={{ padding: 24, maxWidth: 900, margin: '0 auto' }}>
    <Typography.Title>xlsx 导入</Typography.Title>
    <Typography.Paragraph>仅支持 .xlsx；上传解析后先预览，确认前不会写入正式条目。</Typography.Paragraph>
    <Upload accept=".xlsx" showUploadList={false} beforeUpload={(file) => { if (!file.name.toLowerCase().endsWith('.xlsx')) { message.error('仅支持 xlsx 文件'); return Upload.LIST_IGNORE; } if (file.size > 10 * 1024 * 1024) { message.error('文件不能超过 10MB'); return Upload.LIST_IGNORE; } return true; }} customRequest={async ({ file, onSuccess, onError }) => { try { const result = await importApi.xlsxPreview(taskId!, file as File); setPreview(result); onSuccess?.(result); } catch (error) { onError?.(error as Error); } }}><Button type="primary">选择 xlsx 文件</Button></Upload>
    {preview && <><Alert style={{ marginTop: 16 }} type={preview.errorRows.length ? 'warning' : 'info'} message={`共 ${preview.totalRows} 行，有效 ${preview.validRows} 行，错误 ${preview.errorRows.length} 行`} description={preview.errorRows.map((row) => `第${row.rowNumber}行：${row.reason}`).join('；')} />
      <List style={{ marginTop: 16 }} bordered dataSource={preview.candidates} locale={{ emptyText: <Empty description="没有候选条目" /> }} renderItem={(candidate) => <List.Item>{candidate.title}{candidate.duplicate ? '（疑似重复，默认跳过）' : ''}</List.Item>} />
      <Button style={{ marginTop: 16 }} type="primary" loading={confirm.isPending} disabled={!preview.candidates.length} onClick={() => confirm.mutate()}>确认入库</Button>
    </>}
  </main>;
}
