import request from './request';
import type {
  ApiResponse,
  ResumeVO,
  PageResult,
  CreateResumeRequest,
  UpdateResumeRequest,
  PolishRequest,
  PolishResponse,
} from '../types';

export interface ResumeListParams {
  keyword?: string;
  page?: number;
  pageSize?: number;
}

export type ExportFormat = 'html' | 'txt' | 'json';

/**
 * 归一化简历列表响应：兼容两种后端返回形态
 *  - 无分页参数时：`{ code, message, data: ResumeVO[] }`（data 为直接数组）
 *  - 带 page/pageSize 时：`{ code, message, data: { data: ResumeVO[], total, ... } }`（PageResult 包裹）
 * 之前 InterviewStart 直接按 PageResult 解析 `res.data.data.data`，
 * 在无参数（返回数组）时把 `array.data` 读成 undefined → 列表为空 → 关联简历选择器不渲染。
 */
export function normalizeResumeList(
  body: ApiResponse<ResumeVO[] | PageResult<ResumeVO>>,
): { list: ResumeVO[]; total: number } {
  const payload = body.data;
  if (Array.isArray(payload)) {
    return { list: payload, total: payload.length };
  }
  const pr = payload as PageResult<ResumeVO>;
  return { list: pr?.data ?? [], total: pr?.total ?? 0 };
}

export const resumeApi = {
  create: (data: CreateResumeRequest) =>
    request.post<ApiResponse<ResumeVO>>('/resumes', data),

  update: (id: string | number, data: UpdateResumeRequest) =>
    request.put<ApiResponse<ResumeVO>>(`/resumes/${id}`, data),

  getById: (id: string | number) =>
    request.get<ApiResponse<ResumeVO>>(`/resumes/${id}`),

  // 新后端：返回 Result<PageResult<ResumeVO>>（Result.data 内嵌 PageResult：data=列表、page/pageSize/total 同级），支持 keyword/page/pageSize
  list: (params?: ResumeListParams) =>
    request.get<ApiResponse<PageResult<ResumeVO>>>('/resumes', { params }),

  // 深拷贝简历 + sections（标题加"(副本)"由后端处理）
  duplicate: (id: string | number) =>
    request.post<ApiResponse<ResumeVO>>(`/resumes/${id}/duplicate`),

  // 导出简历为 html/txt/json。返回原始文件流（绕过 JSONbig 解析）
  exportResume: (id: string | number, format: ExportFormat) =>
    request.get<Blob>(`/resumes/${id}/export?format=${format}`, {
      responseType: 'blob',
      transformResponse: [(data: unknown) => data],
    }),

  delete: (id: string | number) =>
    request.delete<ApiResponse<null>>(`/resumes/${id}`),

  setActive: (id: string | number) =>
    request.patch<ApiResponse<ResumeVO>>(`/resumes/${id}/active`),

  // 上传 PDF 解析创建（可选地携带 template，与创建弹窗的模板选择保持一致）
  upload: (file: File, template?: string) => {
    const formData = new FormData();
    formData.append('file', file);
    if (template) formData.append('template', template);
    return request.post<ApiResponse<ResumeVO>>('/resumes/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 60000,
    });
  },

  polish: (data: PolishRequest) =>
    request.post<ApiResponse<PolishResponse>>('/resumes/polish', data, {
      timeout: 60000,
    }),
};
