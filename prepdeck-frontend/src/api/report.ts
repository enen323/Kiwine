import request from './request';
import type {
  ApiResponse,
  InterviewReport,
  GenerateReportRequest,
  InterviewOption,
} from '../types';

/**
 * Report Service 接口封装（对应 api/report-api.md）。
 *
 * 注意：该服务约定通过 `X-User-Id` 请求头识别用户（生产由网关过滤器注入）。
 * 开发态走 vite 代理、无网关，故必须由前端显式携带该头；统一在此模块内
 * 注入，调用方无需关心。userId 来源于 `userApi.getMe()`。
 *
 * 路由前缀：`/api/v1/reports`（vite 统一代理到网关 localhost:8081，由网关路由到 report 服务）。
 */

/** 把后端业务错误码翻译成用户可读的中文提示（HTTP 200 + code != 0）。 */
export function normalizeReportError(code: number, message?: string): string {
  switch (code) {
    case 20000:
      return '登录状态已失效，请重新登录';
    case 60000:
      return '暂无已完成的面试数据，先去完成几场面试吧';
    case 90000:
      return '服务器开小差了，请稍后重试';
    default:
      // 仅当 message 为可读文本（非纯大写英文/数字码）时直接展示，避免泄露原始码值
      if (message && !/^[A-Z0-9_]+$/.test(message)) return message;
      return '生成报告失败，请稍后重试';
  }
}

export const reportApi = {
  /**
   * 生成面试成长报告（POST /reports/generate）。
   * interviewIds 与时间范围二选一；均不传则取用户全量已完成面试。
   */
  generate: (body: GenerateReportRequest, userId: string | number) =>
    request.post<ApiResponse<InterviewReport>>('/reports/generate', body, {
      headers: { 'X-User-Id': String(userId) },
    }),

  /**
   * 拉取当前用户所有已完成面试，供"选择面试"面板使用
   * （GET /reports/interviews/available）。
   */
  availableInterviews: (userId: string | number) =>
    request.get<ApiResponse<InterviewOption[]>>('/reports/interviews/available', {
      headers: { 'X-User-Id': String(userId) },
    }),
};
