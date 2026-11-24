import { fetchEventSource } from '@microsoft/fetch-event-source';
import request from './request';
import type {
  ApiResponse,
  PageResponse,
  InterviewVO,
  InterviewListVO,
  InterviewMessage,
  StartInterviewRequest,
  StartInterviewResponse,
  SubmitAnswerRequest,
  SubmitAnswerResponse,
  ControlRequest,
  CompanyType,
  InterviewStatus,
} from '../types';

/** SSE 事件名（对应文档 4.2） */
const SSE_EVENT_AI_RESPONSE = 'ai_response';

/** 流式回调集合（submitAnswer 与 control 共用，两者 SSE 事件结构相同） */
export interface SubmitAnswerHandlers {
  /** 每收到一帧 `ai_response` 事件时触发；携带完整 SubmitAnswerResponse（终态/聚合）。 */
  onMessage: (data: SubmitAnswerResponse) => void;
  /**
   * 每收到一个 `chunk`（或 `delta`）流式增量事件时触发，参数为该帧携带的
   * AI 回复增量文本。前端应将其追加到当前 AI 消息以呈现打字机/流式效果。
   * 缺失时流式帧会被忽略（这正是此前「无流式输出」的根因）。
   */
  onChunk?: (text: string) => void;
  /** 流异常中断（不含主动 abort）时触发。 */
  onError?: (err: Error) => void;
  /** 流正常结束时触发（此时已收到最终帧）。 */
  onComplete?: () => void;
  /** SSE 连接关闭且非主动 abort 时触发（用于断线重连判断）。 */
  onClose?: () => void;
  /** SSE 连接已成功打开（HTTP 200 + 正确 content-type）时触发。 */
  onReady?: () => void;
}

/**
 * 从 chunk 事件的数据中提取增量文本。
 *
 * 后端约定：`chunk` 事件的 `data` 是 **纯文本字符串**（非 JSON、无字段名），
 * 即 AI 回复的增量片段，前端应逐帧直接追加以呈现流式效果。
 * 因此这里不做任何 JSON 解析，仅做最小清洗：忽略 [DONE] 等结束哨兵。
 * 保留原始文本（含可能的前导/尾随空格），避免拼接相邻 chunk 时丢字。
 */
function extractChunkText(raw: string): string {
  if (!raw) return '';
  const trimmed = raw.trim();
  if (trimmed === '[DONE]' || trimmed === 'DONE') return '';
  return raw;
}

/** 流式 SSE 句柄 */
export interface SubmitAnswerStream {
  /** 主动中断流 */
  abort: () => void;
  /** 流结束（正常或异常）后 resolve；异常时不 reject，错误已通过 onError 投递 */
  done: Promise<void>;
}

/** 读取 SSE 错误响应体里的 code + message 字段（兼容 Result 错误结构） */
async function readErrorInfo(
  response: Response,
  fallback: string,
): Promise<{ code: number | null; message: string }> {
  try {
    const body = await response.json();
    const code = typeof body?.code === 'number' ? body.code : null;
    const msg = body?.message ?? body?.data?.message;
    return { code, message: typeof msg === 'string' && msg ? msg : fallback };
  } catch {
    return { code: null, message: fallback };
  }
}

/** 给 Error 附加后端业务码，便于上层按 code 区分处理（如 50001 面试已结束）。 */
function withCode(err: Error, code: number | null): Error {
  if (code != null) (err as Error & { code?: number }).code = code;
  return err;
}

/**
 * 面试 SSE 流式请求的共享实现。
 *
 * `submitAnswer`（POST /{id}/answer）和 `control`（POST /{id}/control）均返回
 * `SseEmitter`，事件结构完全相同（chunk / ai_response / heartbeat / error），
 * 因此共用此 helper，仅 URL 和请求体不同。
 *
 * - `chunk`/`delta` 事件 → `handlers.onChunk(text)`（增量文本帧）
 * - `ai_response`/`message` 事件 → `handlers.onMessage(payload)`（聚合终态帧）
 * - 后端推完不主动关连接，空闲 IDLE_MS 后主动 abort（面试结束立即 abort）
 */
function createInterviewSSEStream(
  url: string,
  body: unknown,
  handlers: SubmitAnswerHandlers,
): SubmitAnswerStream {
  const controller = new AbortController();
  const token = localStorage.getItem('accessToken') ?? '';

  let errored = false;

  const IDLE_MS = 3000;
  let idleTimer: ReturnType<typeof setTimeout> | null = null;
  const clearIdle = () => {
    if (idleTimer !== null) {
      clearTimeout(idleTimer);
      idleTimer = null;
    }
  };
  const armIdleAbort = () => {
    clearIdle();
    idleTimer = setTimeout(() => {
      controller.abort();
    }, IDLE_MS);
  };

  const done = fetchEventSource(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(body),
    signal: controller.signal,
    /**
     * 停用库内置的 visibilitychange 处理：默认实现会在标签页隐藏时主动 abort
     * 当前请求、可见时再自动重建连接（即无脑重发 POST）。这里改为 true，
     * 让浏览器/后端自己管理连接生命周期，断线检测与重连完全交由前端
     * InterviewChat 的 scheduleReconnect 逻辑按指数退避（最多 3 次）处理。
     */
    openWhenHidden: true,
    async onopen(response) {
      if (!response.ok) {
        const { code, message } = await readErrorInfo(
          response,
          `请求失败（${response.status}）`,
        );
        throw withCode(new Error(message), code);
      }
      const contentType = response.headers.get('content-type') ?? '';
      if (!contentType.includes('text/event-stream')) {
        throw new Error(
          `服务端未返回 SSE 流（期望 text/event-stream，实际 ${contentType || '未知'}）`,
        );
      }
    },
    onmessage(ev) {
      const eventName = ev.event || 'message';
      try {
        if (eventName === 'chunk' || eventName === 'delta') {
          const text = extractChunkText(ev.data);
          if (text) handlers.onChunk?.(text);
          armIdleAbort();
          return;
        }
        if (eventName === SSE_EVENT_AI_RESPONSE || eventName === 'message') {
          const payload = JSON.parse(ev.data) as SubmitAnswerResponse;
          handlers.onMessage(payload);
          if (payload.interviewCompleted) {
            clearIdle();
            controller.abort();
          } else {
            armIdleAbort();
          }
          return;
        }
        // 处理服务端推送的 SSE error 事件（Sentinel 熔断降级 / AI 异常 / 状态守卫）
        if (eventName === 'error') {
          let msg = ev.data || 'AI 服务暂时不可用，请稍后重试';
          let code: number | null = null;
          // 个别实现会把 Result JSON 作为 error 事件数据推送，尽量解析出 code
          try {
            const parsed = JSON.parse(ev.data);
            if (typeof parsed?.code === 'number') code = parsed.code;
            if (typeof parsed?.message === 'string' && parsed.message) msg = parsed.message;
          } catch {
            /* 纯文本错误，code 保持 null */
          }
          handlers.onError?.(withCode(new Error(msg), code));
          errored = true;
          clearIdle();
          controller.abort();
          return;
        }
      } catch {
        /* 忽略非法 JSON 帧 */
      }
    },
    onerror(err) {
      if (err instanceof Error && err.name === 'AbortError') throw err;
      if (!errored) {
        errored = true;
        handlers.onError?.(
          err instanceof Error ? err : new Error(String(err)),
        );
      }
      throw err;
    },
  })
    .then(() => {
      handlers.onComplete?.();
      handlers.onClose?.();
    })
    .catch((err: unknown) => {
      if (
        err instanceof Error &&
        (err.name === 'AbortError' || errored)
      ) {
        handlers.onClose?.();
        return;
      }
      errored = true;
      handlers.onError?.(err instanceof Error ? err : new Error(String(err)));
      handlers.onClose?.();
    });

  return {
    abort: () => {
      clearIdle();
      controller.abort();
    },
    done,
  };
}

export const interviewApi = {
  start: (data: StartInterviewRequest) =>
    request.post<ApiResponse<StartInterviewResponse>>('/interviews', data),

  /**
   * 提交回答（SSE 流式，对应文档 3.2）。
   *
   * 后端返回 `text/event-stream`。混合两类事件：
   * - `chunk`：AI 回复增量帧，每帧 `data` 为纯文本字符串，无字段名，应逐帧
   *   追加到当前 AI 消息以呈现打字机/流式效果（通过 handlers.onChunk 投递）。
   * - `ai_response`：终态/聚合帧，携带完整 SubmitAnswerResponse（含 userAnswer、
   *   aiResponse 全文、评分、interviewCompleted 等），以最后一帧为最终结果。
   * 超时 5 分钟。
   *
   * 因 EventSource 不支持 POST + 自定义 header，这里采用
   * `@microsoft/fetch-event-source`。注：简历 AI 对话（/resumes/ai-chat）已改为
   * 普通 HTTP POST，本面试流程仍保留 SSE 流式。
   */
  submitAnswer: (
    id: string | number,
    data: SubmitAnswerRequest,
    handlers: SubmitAnswerHandlers,
  ): SubmitAnswerStream =>
    createInterviewSSEStream(
      `/api/v1/interviews/${id}/answer`,
      data,
      handlers,
    ),

  /**
   * 控制指令（SSE 流式，对应文档 3.8 `POST /{id}/control`）。
   *
   * 发送控制指令（跳过 / 提示 / 提前结束），SSE 事件结构与 submitAnswer 相同：
   * - `chunk` → AI 回复增量（SKIP 时为下一个问题，HINT 时为思路提示，END 时为总结）
   * - `ai_response` → 终态聚合帧（interviewCompleted 在 END 时为 true）
   *
   * 面试状态为 CREATED 时不可发送控制指令。
   */
  control: (
    id: string | number,
    data: ControlRequest,
    handlers: SubmitAnswerHandlers,
  ): SubmitAnswerStream =>
    createInterviewSSEStream(
      `/api/v1/interviews/${id}/control`,
      data,
      handlers,
    ),

  getById: (id: string | number) =>
    request.get<ApiResponse<InterviewVO>>(`/interviews/${id}`),

  list: (params?: {
    company?: CompanyType;
    status?: InterviewStatus;
    page?: number;
    pageSize?: number;
  }) =>
    request.get<ApiResponse<PageResponse<InterviewListVO>>>('/interviews', { params }),

  getMessages: (id: string | number) =>
    request.get<ApiResponse<InterviewMessage[]>>(`/interviews/${id}/messages`),

  terminate: (id: string | number) =>
    request.post<ApiResponse<null>>(`/interviews/${id}/terminate`),

  /**
   * 删除面试记录（DELETE，对应文档「删除面试记录」）。
   *
   * - 已完成面试（COMPLETED）：后端软删除（标记 deleted=true），数据保留供报告模块使用。
   * - 其他状态（CREATED / IN_PROGRESS / TERMINATED）：后端硬删除（物理删除）。
   * - 接口幂等：已删除记录再次调用返回 404。
   *
   * 删除成功后前端应刷新列表（本前端无本地缓存，重新拉取即可）。
   */
  delete: (id: string | number) =>
    request.delete<ApiResponse<null>>(`/interviews/${id}`),

  /** 健康检查（无需认证，对应文档 3.9） */
  health: () => request.get<ApiResponse<string>>('/interviews/health'),
};
