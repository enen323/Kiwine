import request from './request';
import { userApi } from './user';
import type { ApiResponse, PageResponse } from '../types';
import type {
  QuestionDetailVO,
  QuestionTag,
  DailyQuestion,
  QuestionListParams,
  QuestionRecommendation,
} from '../types/question';

/**
 * Question Service 接口封装（对应 question-service-api.md）。
 *
 * 服务路由前缀由 vite 统一代理到网关 localhost:8081（见 vite.config.ts），由网关转发到 question 服务。
 * 文档约定：用户身份由网关注入的 `X-User-Id` 请求头识别。开发态无网关，
 * 故由前端显式携带该头（模块内统一解析 userApi.getMe() 并缓存，调用方无需关心）。
 */

let cachedUserId: Promise<string | number> | null = null;
function resolveUserId(): Promise<string | number> {
  if (!cachedUserId) {
    cachedUserId = userApi.getMe().then((res) => res.data.data.id);
  }
  return cachedUserId;
}

async function withUserHeaders(): Promise<Record<string, string>> {
  const uid = await resolveUserId();
  return { 'X-User-Id': String(uid) };
}

/** 把后端业务错误码翻译成用户可读中文提示（HTTP 200 + code != 0）。 */
export function normalizeQuestionError(code: number, message?: string): string {
  switch (code) {
    case 80000:
      return '题目不存在或已下架';
    case 80001:
      return '该题已被收藏';
    case 80002:
      return '该题尚未收藏';
    case 80003:
      return '标签不存在';
    case 80004:
      return '标签已存在';
    case 80005:
      return '今日一题不存在';
    case 80006:
      return '暂无足够面试数据，已为你推荐热门题目';
    case 10002:
      return '请求参数校验不通过';
    case 90000:
      return '服务器开小差了，请稍后重试';
    case 20000:
      return '登录状态已失效，请重新登录';
    default:
      // 仅当 message 为可读文本（非纯大写英文/数字码）时直接展示，避免泄露原始码值
      if (message && !/^[A-Z0-9_]+$/.test(message)) return message;
      return '操作失败，请稍后重试';
  }
}

/** 数组参数按 `tagIds=1&tagIds=2` 形式序列化（贴合 Spring 后端绑定，避免默认的 `tagIds[]`）。 */
function listParamsSerializer(params: Record<string, unknown>): string {
  const usp = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value === undefined || value === null || value === '') return;
    if (Array.isArray(value)) {
      value.forEach((item) => usp.append(key, String(item)));
    } else {
      usp.append(key, String(value));
    }
  });
  return usp.toString();
}

export const questionApi = {
  /** GET /questions —— 题目列表（分页 / 筛选 / 排序 / 搜索）。 */
  list: async (params: QuestionListParams = {}) => {
    const headers = await withUserHeaders();
    return request.get<ApiResponse<PageResponse<QuestionDetailVO>>>('/questions', {
      params: {
        page: params.page ?? 1,
        pageSize: params.pageSize ?? 20,
        ...(params.sortBy ? { sortBy: params.sortBy } : {}),
        ...(params.order ? { order: params.order } : {}),
        ...(params.type ? { type: params.type } : {}),
        ...(params.difficulty ? { difficulty: params.difficulty } : {}),
        ...(params.tagIds && params.tagIds.length ? { tagIds: params.tagIds } : {}),
        ...(params.keyword ? { keyword: params.keyword } : {}),
      },
      paramsSerializer: listParamsSerializer,
      headers,
    });
  },

  /** GET /questions/search —— 基于 PostgreSQL tsvector 的全文检索（AND/OR/- 语法）。 */
  search: async (keyword: string, page = 1, pageSize = 20) => {
    const headers = await withUserHeaders();
    return request.get<ApiResponse<PageResponse<QuestionDetailVO>>>('/questions/search', {
      params: { keyword, page, pageSize },
      headers,
    });
  },

  /** GET /questions/{id} —— 题目详情（同时 +1 浏览量、标记每日一题已完成）。 */
  detail: async (id: string | number) => {
    const headers = await withUserHeaders();
    return request.get<ApiResponse<QuestionDetailVO>>(`/questions/${id}`, { headers });
  },

  /** GET /questions/daily —— 每日一题（同一用户同日幂等）。 */
  daily: async () => {
    const headers = await withUserHeaders();
    return request.get<ApiResponse<DailyQuestion>>('/questions/daily', { headers });
  },

  /** POST /questions/daily/complete —— 完成今日一题（completed 变为 true）。 */
  completeDaily: async () => {
    const headers = await withUserHeaders();
    return request.post<ApiResponse<DailyQuestion>>('/questions/daily/complete', null, {
      headers,
    });
  },

  /** GET /questions/recommendations —— 智能推荐题目（§3.1）。count 默认 10，最大 50。 */
  recommendations: async (count = 10) => {
    const headers = await withUserHeaders();
    return request.get<ApiResponse<QuestionRecommendation>>('/questions/recommendations', {
      params: { count: Math.min(Math.max(count, 1), 50) },
      headers,
    });
  },

  /** POST /questions/{id}/practice —— 记录做题行为（§3.2，幂等）。 */
  practice: async (id: string | number) => {
    const headers = await withUserHeaders();
    return request.post<ApiResponse<null>>(`/questions/${id}/practice`, null, { headers });
  },

  /** GET /questions/favorites —— 我的收藏列表（分页）。 */
  favorites: async (page = 1, pageSize = 20) => {
    const headers = await withUserHeaders();
    return request.get<ApiResponse<PageResponse<QuestionDetailVO>>>('/questions/favorites', {
      params: { page, pageSize },
      headers,
    });
  },

  /** POST /questions/favorites —— 收藏题目。 */
  addFavorite: async (questionId: string | number) => {
    const headers = await withUserHeaders();
    return request.post<ApiResponse<null>>(
      '/questions/favorites',
      { questionId },
      { headers },
    );
  },

  /** DELETE /questions/favorites/{questionId} —— 取消收藏。 */
  removeFavorite: async (questionId: string | number) => {
    const headers = await withUserHeaders();
    return request.delete<ApiResponse<null>>(`/questions/favorites/${questionId}`, {
      headers,
    });
  },

  /** GET /tags —— 全部标签（后台维护，无需鉴权）。 */
  tags: async () => {
    return request.get<ApiResponse<QuestionTag[]>>('/tags');
  },
};
