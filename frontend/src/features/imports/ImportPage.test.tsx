import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http } from 'msw';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { ImportPage } from '@/features/imports/ImportPage';
import { error, ok, server } from '@/test/msw';

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(<QueryClientProvider client={client}><MemoryRouter initialEntries={['/tasks/task-1/import']}><Routes><Route path="/tasks/:taskId/import" element={<ImportPage />} /></Routes></MemoryRouter></QueryClientProvider>);
}

const preview = { totalRows: 2, validRows: 1, errorRows: [{ rowNumber: 2, reason: '标题为空' }], candidates: [{ title: '导入题目' }] };

describe('ImportPage', () => {
  it('renders preview errors and sends multipart upload without JSON content type', async () => {
    let request: Request | undefined;
    server.use(http.post('/api/v1/tasks/task-1/imports/xlsx/preview', async ({ request: incoming }) => { request = incoming; return ok(preview); }));
    renderPage();
    const file = new File(['xlsx-data'], 'questions.xlsx', { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
    fireEvent.change(document.querySelector('input[type="file"]')!, { target: { files: [file] } });
    expect(await screen.findByText(/第2行：标题为空/, {}, { timeout: 10000 })).toBeInTheDocument();
    expect(request).toBeDefined();
    expect(request!.headers.get('content-type')).toMatch(/^multipart\/form-data; boundary=/);
    expect(request!.headers.get('content-type')).not.toContain('application/json');
  });

  it('retains preview when confirmation fails', async () => {
    server.use(
      http.post('/api/v1/tasks/task-1/imports/xlsx/preview', () => ok(preview)),
      http.post('/api/v1/tasks/task-1/imports/xlsx/confirm', () => error(409, 'CONFLICT', '确认失败')),
    );
    renderPage();
    const user = userEvent.setup();
    fireEvent.change(document.querySelector('input[type="file"]')!, { target: { files: [new File(['xlsx'], 'questions.xlsx', { type: 'application/octet-stream' })] } });
    await user.click(await screen.findByRole('button', { name: /确认入库/ }));
    await waitFor(() => expect(screen.getByText(/共 2 行/)).toBeInTheDocument(), { timeout: 10000 });
    expect(screen.getByText('导入题目')).toBeInTheDocument();
  });
});
