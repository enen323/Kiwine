// 投递记录 API 层（对齐 job-service-api.md §一）
// 约定：
// - 统一走项目已有的 axios 实例 `request`（baseURL /api/v1，自动携带 Authorization）。
// - 响应信封为项目统一的 ApiResponse<T> = { code, message, data }，code===0 为成功。
// - 列表接口返回 ApiResponse<JobApplicationPage>，即 data 为 { items, total, page, pageSize, totalPages }。
import request from '@/api/request';
import { resumeApi } from '@/api/resume';
import type { ApiResponse } from '@/types';
import { normalizeApplicationError } from '../utils';
import type {
  JobApplication,
  JobApplicationPage,
  JobApplicationPayload,
  ResumeOption,
} from '../types';

/** 解包 ApiResponse：失败抛出携带 code 的 Error(message)，成功返回 data。 */
export function unwrap<T>(res: { data: ApiResponse<T> }): T {
  const body = res.data;
  if (body.code !== 0) {
    const e = new Error(normalizeApplicationError(body.code, body.message));
    (e as { code?: number }).code = body.code;
    throw e;
  }
  return body.data;
}

export const applicationApi = {
  /** 列表：GET /applications?status&keyword&page&pageSize */
  list: (params: {
    status?: string;
    keyword?: string;
    page?: number;
    pageSize?: number;
    signal?: AbortSignal;
  }) =>
    request.get<ApiResponse<JobApplicationPage>>('/applications', {
      params: {
        status: params.status,
        keyword: params.keyword,
        page: params.page,
        pageSize: params.pageSize,
      },
      signal: params.signal,
    }),

  /** 详情：GET /applications/:id */
  get: (id: string) =>
    request.get<ApiResponse<JobApplication>>(`/applications/${id}`),

  /** 新增：POST /applications */
  create: (payload: JobApplicationPayload) =>
    request.post<ApiResponse<JobApplication>>('/applications', payload),

  /** 更新：PUT /applications/:id */
  update: (id: string, payload: JobApplicationPayload) =>
    request.put<ApiResponse<JobApplication>>(`/applications/${id}`, payload),

  /** 删除：DELETE /applications/:id */
  remove: (id: string) =>
    request.delete<ApiResponse<null>>(`/applications/${id}`),
};

/**
 * 关联简历下拉数据源：取简历列表前 100 条，映射为 { id, title }。
 * 列表接口（非删除态）无需鉴权之外的额外处理，失败由调用方兜底。
 */
export async function fetchResumeOptions(): Promise<ResumeOption[]> {
  const res = await resumeApi.list({ page: 1, pageSize: 100 });
  const page = unwrap<{ data: Array<{ id: string | number; title?: string; fileName: string }> }>(res);
  return (page?.data ?? []).map((r) => ({
    id: String(r.id),
    title: r.title || r.fileName,
  }));
}
