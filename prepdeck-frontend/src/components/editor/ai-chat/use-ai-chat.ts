import { useCallback, useRef, useState } from 'react';
import request from '@/api/request';
import type { AiChatComplete, ChatMessage } from '@/types';

const AI_CHAT_ENDPOINT = '/resumes/ai-chat';

/** sessionId 按 resumeId 隔离，存入 localStorage 的 key 前缀。 */
export const RESUME_AI_SESSION_KEY = 'resume:ai:session:';

/** AI 当前抛出的待回答问题（含选项），由输入区渲染为「提问卡」。 */
export interface PendingQuestion {
  content: string;
  options: string[];
}

export interface UseAiChatReturn {
  messages: ChatMessage[];
  isConnecting: boolean;
  isComplete: boolean;
  result: AiChatComplete | null;
  error: string | null;
  /** 输入区提问卡：AI 通过 options 消息抛出的问题 + 选项。 */
  pendingQuestion: PendingQuestion | null;
  sendMessage: (text: string) => void;
  retry: () => void;
  reset: () => void;
}

/** 后端普通 HTTP 响应体（后端已将 SSE 改为普通 POST，单次返回整包）。 */
export interface AiChatResponse {
  type: 'text' | 'options' | 'complete';
  content: string;
  options?: string[];
  /** 仅当 type === 'complete' 时存在，为生成好的简历正文。 */
  result?: AiChatComplete;
}

/**
 * 管理 AI 对话式简历生成（普通 HTTP POST，无 SSE）。
 *
 * 约定（对齐后端设计）：
 * - 每次用户消息触发一次 POST，服务端返回单个 JSON 响应：
 *   { type, content, options?, result? }
 *   - type === 'text'    → 追加一条 assistant 消息。
 *   - type === 'options' → 把问题 + 选项抛到输入区作为「提问卡」。
 *   - type === 'complete'→ 对话结束，result 即生成好的简历，无需再轮询结果接口。
 * - 响应解析兼容两种包裹：{ type, ... } 或 { code, message, data: { type, ... } }。
 */
export function useAiChat(sessionId: string | null): UseAiChatReturn {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [isConnecting, setIsConnecting] = useState(false);
  const [isComplete, setIsComplete] = useState(false);
  const [result, setResult] = useState<AiChatComplete | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [pendingQuestion, setPendingQuestion] = useState<PendingQuestion | null>(null);

  const abortRef = useRef<AbortController | null>(null);
  const lastUserTextRef = useRef('');
  const lastUserIndexRef = useRef(0);

  const sendMessage = useCallback(
    (text: string) => {
      if (!sessionId || !text.trim() || isComplete) return;

      const trimmed = text.trim();
      lastUserTextRef.current = trimmed;

      // 把当前 AI 提问（输入区提问卡）一并写入聊天流，
      // 选完选项 / 回答后，界面即可同时看到「AI 的问题」与「用户的回答」（类似微信历史）。
      // 仅当存在提问卡时才写（首轮起手按钮无前置提问，跳过）。
      if (pendingQuestion) {
        setMessages((prev) => [...prev, { role: 'assistant', content: pendingQuestion.content }]);
      }

      // 用户作出回应（无论点选项还是自由输入），清除输入区提问卡
      setPendingQuestion(null);

      // 1. 追加用户消息（记录插入位置，供 retry 截断）
      setMessages((prev) => {
        lastUserIndexRef.current = prev.length;
        return [...prev, { role: 'user', content: trimmed }];
      });
      setError(null);
      setIsConnecting(true);

      // 关闭上一次可能仍在进行的请求，开启本轮请求
      abortRef.current?.abort();
      const controller = new AbortController();
      abortRef.current = controller;

      request
        .post<AiChatResponse>(AI_CHAT_ENDPOINT, { sessionId, message: trimmed }, {
          signal: controller.signal,
          timeout: 120000,
        })
        .then((res) => {
          // 业务错误：HTTP 200 但 code != 0
          // （例如配额不足时后端返回 { code: 70000, message: '本月免费额度已用完', data: null }）
          const raw = res.data as unknown as Record<string, unknown>;
          if (typeof raw.code === 'number' && raw.code !== 0) {
            setIsConnecting(false);
            setError(typeof raw.message === 'string' ? raw.message : '请求失败，请稍后重试');
            return;
          }
          const payload = normalizeResponse(res.data);
          if (!payload) {
            setIsConnecting(false);
            setError('响应格式异常，请点击重试');
            return;
          }
          handlePayload(payload);
        })
        .catch((err: unknown) => {
          // 主动 abort（retry / reset）属于预期内，忽略。
          if (err instanceof Error && err.name === 'AbortError') return;
          setIsConnecting(false);
          // 优先从 axios 错误响应体中提取后端 message
          const axiosMsg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
          setError(axiosMsg || '连接中断，请点击重试');
        });

      // 在闭包内处理单包响应，便于在 catch 前复用
      function handlePayload(payload: AiChatResponse) {
        setIsConnecting(false);
        switch (payload.type) {
          case 'options':
            setPendingQuestion({
              content: payload.content,
              options: payload.options ?? [],
            });
            break;
          case 'complete':
            setResult(payload.result ?? null);
            setIsComplete(true);
            setPendingQuestion(null);
            break;
          case 'text':
          default:
            setMessages((prev) => [
              ...prev,
              { role: 'assistant', content: payload.content },
            ]);
            break;
        }
      }
    },
    [sessionId, isComplete, pendingQuestion],
  );

  /** 错误后重试：回滚到本轮用户消息之前，重新发起同一请求（不重复追加）。 */
  const retry = useCallback(() => {
    if (!lastUserTextRef.current) return;
    setMessages((prev) => prev.slice(0, lastUserIndexRef.current));
    sendMessage(lastUserTextRef.current);
  }, [sendMessage]);

  const reset = useCallback(() => {
    abortRef.current?.abort();
    abortRef.current = null;
    setMessages([]);
    setIsConnecting(false);
    setIsComplete(false);
    setResult(null);
    setError(null);
    setPendingQuestion(null);
  }, []);

  return { messages, isConnecting, isComplete, result, error, pendingQuestion, sendMessage, retry, reset };
}

/**
 * 将响应体归一化为 AiChatResponse，兼容后端多种可能形态：
 *   - 直接包裹：{ type, content, options?, result? }
 *   - ApiResponse 包裹：{ code, message, data: { type, ... } }
 *   - complete 未带 type（沿用旧 SSE 形态）：{ content: AiChatComplete } 或
 *     { result: AiChatComplete }，或整包即简历（含 title）。
 * 返回 null 表示无法解析出有效结构（会在控制台打印原始响应以便排查）。
 */
function normalizeResponse(raw: unknown): AiChatResponse | null {
  // 兼容后端把 JSON 包成字符串的情况
  if (typeof raw === 'string') {
    try {
      return normalizeResponse(JSON.parse(raw));
    } catch {
      return null;
    }
  }
  if (!raw || typeof raw !== 'object') return null;

  const obj = raw as Record<string, unknown>;
  // 兼容 ApiResponse 包裹：{ code, message, data }
  let candidate: Record<string, unknown> = obj;
  if (obj.data && typeof obj.data === 'object') {
    candidate = obj.data as Record<string, unknown>;
  }

  // 标准形态：带有 type 字段
  if (typeof candidate.type === 'string') {
    // complete 时若简历放在 content（旧 SSE 形态）而非 result，统一收归到 result
    if (
      candidate.type === 'complete' &&
      !candidate.result &&
      candidate.content &&
      typeof candidate.content === 'object'
    ) {
      return {
        ...candidate,
        result: candidate.content as unknown as AiChatComplete,
      } as unknown as AiChatResponse;
    }
    return candidate as unknown as AiChatResponse;
  }

  // 兜底：complete 响应未带 type，直接以 content / result / 自身作为简历返回
  const maybeResume =
    (candidate.result as Record<string, unknown> | undefined) ??
    (candidate.content as Record<string, unknown> | undefined) ??
    (typeof candidate.title === 'string' ? candidate : undefined);
  if (maybeResume && typeof maybeResume === 'object') {
    return {
      type: 'complete',
      content: '',
      result: maybeResume as unknown as AiChatComplete,
    };
  }

  console.error('[ai-chat] 无法识别的响应格式：', raw);
  return null;
}
