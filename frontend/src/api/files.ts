import { api, unwrap } from './request';
import type { FileResponse } from '../types';

export async function uploadPaperFile(file: File) {
  const form = new FormData();
  form.append('file', file);
  return unwrap<FileResponse>(
    api.post('/api/files/papers', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 120000
    })
  );
}

export async function fetchPdfPreview(fileId: number) {
  const response = await api.get<ArrayBuffer>(`/api/files/papers/${fileId}/preview`, {
    responseType: 'arraybuffer',
    timeout: 120000
  });
  return response.data;
}
