// AI 求职信 API 层（对齐 job-service-api.md §二）
// 端点：
//  - POST   /applications/{applicationId}/cover-letters              生成（关联投递记录）
//  - GET    /ai/resumes/{resumeId}/cover-letters                     按简历列出
//  - GET    /ai/resumes/{resumeId}/cover-letters/{coverLetterId}     查询单封
//  - PUT    /ai/resumes/{resumeId}/cover-letters/{coverLetterId}     更新元数据
//  - DELETE /ai/resumes/{resumeId}/cover-letters/{coverLetterId}     删除
import request from '@/api/request';
import type { ApiResponse } from '@/types';
import { normalizeApplicationError } from '../utils';
import type {
  AiCoverLetter,
  GenerateCoverLetterPayload,
  UpdateCoverLetterPayload,
} from '../types';

/** 解包 ApiResponse：失败抛出携带 code 的 Error(message)，成功返回 data。 */
function unwrap<T>(res: { data: ApiResponse<T> }): T {
  const body = res.data;
  if (body.code !== 0) {
    const e = new Error(normalizeApplicationError(body.code, body.message));
    (e as { code?: number }).code = body.code;
    throw e;
  }
  return body.data;
}

export const coverLetterApi = {
  /** 生成求职信（关联投递记录），耗时 3-10s，单次消耗 1 次 AI 额度 */
  generate: (applicationId: string, payload: GenerateCoverLetterPayload) =>
    request
      .post<ApiResponse<AiCoverLetter>>(
        `/applications/${applicationId}/cover-letters`,
        payload,
      )
      .then((res) => unwrap<AiCoverLetter>(res)),

  /** 按简历列出求职信 */
  listByResume: (resumeId: string) =>
    request
      .get<ApiResponse<AiCoverLetter[]>>(`/ai/resumes/${resumeId}/cover-letters`)
      .then((res) => unwrap<AiCoverLetter[]>(res)),

  /** 查询单封求职信 */
  get: (resumeId: string, coverLetterId: string) =>
    request
      .get<ApiResponse<AiCoverLetter>>(
        `/ai/resumes/${resumeId}/cover-letters/${coverLetterId}`,
      )
      .then((res) => unwrap<AiCoverLetter>(res)),

  /** 更新求职信元数据（不重新生成内容） */
  update: (resumeId: string, coverLetterId: string, payload: UpdateCoverLetterPayload) =>
    request
      .put<ApiResponse<AiCoverLetter>>(
        `/ai/resumes/${resumeId}/cover-letters/${coverLetterId}`,
        payload,
      )
      .then((res) => unwrap<AiCoverLetter>(res)),

  /** 删除求职信 */
  remove: (resumeId: string, coverLetterId: string) =>
    request
      .delete<ApiResponse<null>>(`/ai/resumes/${resumeId}/cover-letters/${coverLetterId}`)
      .then((res) => unwrap<null>(res)),
};
