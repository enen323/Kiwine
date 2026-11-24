import { useState, useEffect, useRef, useCallback, Fragment } from 'react';
import type { FormEvent } from 'react';
import { useParams, useNavigate, useLocation } from 'react-router-dom';
import { interviewApi } from '../api/interview';
import type { SubmitAnswerStream, SubmitAnswerHandlers } from '../api/interview';
import type {
  InterviewVO, InterviewMessage,
  InterviewStage,
  ControlCommand,
} from '../types';
import { InterviewStageMap, StageOrder, INTERVIEW_ERROR_CODES } from '../types';
import { stripRoundCompleteToken, sanitizeMessages, isInterviewOver } from '../lib/interview/sanitize';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';
import { cn } from '@/lib/utils';

/** 万能数组提取：兼容多种后端嵌套格式 */
function extractArray(d: unknown): InterviewMessage[] {
  if (Array.isArray(d)) return d;
  if (!d || typeof d !== 'object') return [];
  for (const key of ['data', 'records', 'content', 'list', 'items', 'rows'])
    if (Array.isArray((d as Record<string, unknown>)[key]))
      return (d as Record<string, unknown>)[key] as InterviewMessage[];
  return [];
}

/**
 * 流式 chunk 渲染的「尾部保留」长度。
 * `[ROUND_COMPLETE]` 等变体最长 16 字符，留 32 的余量可保证任何未闭合的
 * 标记变体（被切成多段跨 chunk 到达）始终落在保留区内，直到流结束才判定，
 * 因此屏幕上不会出现脏标记闪现。
 */
const STREAM_TAIL = 32;

/**
 * 返回当前可安全渲染的流式前缀：去掉末尾 STREAM_TAIL 个字符（可能含未闭合的
 * 标记变体），并对已收到的完整部分做标记清洗。返回空串表示缓冲还不够长。
 */
function streamRenderPrefix(accumulated: string): string {
  if (accumulated.length <= STREAM_TAIL) return '';
  return stripRoundCompleteToken(accumulated.slice(0, accumulated.length - STREAM_TAIL));
}

/** 秒数格式化为「X分Y秒」（面试时长展示用） */
function formatDuration(sec: number): string {
  const s = Math.max(0, Math.floor(sec));
  const m = Math.floor(s / 60);
  return `${m}分${s % 60}秒`;
}

export default function InterviewChat() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const location = useLocation();
  const chatEndRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);

  const [interview, setInterview] = useState<InterviewVO | null>(null);
  const [messages, setMessages] = useState<InterviewMessage[]>([]);
  // 本地消息镜像（供异步重连逻辑读取最新消息，避免闭包拿到陈旧值）
  const messagesRef = useRef<InterviewMessage[]>([]);
  messagesRef.current = messages;
  const [input, setInput] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [completed, setCompleted] = useState(false);
  /**
   * 统一的面试结束态派生判定（三来源并集）：
   * SSE `interviewCompleted` / `GET /{id}` 的 status / `currentStage === 'COMPLETED'`。
   * 组件内所有「是否结束」判断都以此为准，避免散落判断。
   */
  const over = isInterviewOver(interview) || completed;
  // over 的镜像，供异步重连回调读取最新值（避免闭包拿到提交时刻的陈旧 over）
  const overRef = useRef(over);
  overRef.current = over;
  // 是否已开始收到 AI 回复（chunk 或 ai_response 任一），用于隐藏「输入中转圈」
  const [aiStarted, setAiStarted] = useState(false);
  /**
   * 当前正在流式输出的消息 id（占位 id 或后端真实 id）。
   * 用显式状态锁定「光标应该打在哪条消息上」，避免用「最后一条 AI」反推时，
   * 在「用户刚提交、流式占位尚未创建」的空窗期把光标错误地打到上一条已完成
   * 的 AI 消息上导致闪烁。
   */
  const [streamingId, setStreamingId] = useState<string | number | null>(null);

  // SSE 连接追踪
  const streamRef = useRef<SubmitAnswerStream | null>(null);
  const streamingRef = useRef(false);

  // 断线重连状态
  const [connBanner, setConnBanner] = useState<{ text: string; tone: 'reconnecting' | 'recovered' | 'failed' } | null>(null);
  const reconnectingRef = useRef(false);
  const reconnectAttemptsRef = useRef(0);
  const pendingSubmitRef = useRef<{ kind: 'answer'; content: string } | { kind: 'control'; command: ControlCommand } | null>(null);
  const resubmittedRef = useRef(false);
  const reconnectTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  /** 本地乐观消息的临时 id 集合，重连合并时按此剔除已被后端覆盖的临时消息 */
  const tempIdsRef = useRef<Set<string>>(new Set());

  // scroll to bottom
  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  // 输入框纵向自适应：内容增多时高度增长，封顶（200px）后内部滚动
  const MAX_INPUT_HEIGHT = 200;
  const autoResize = () => {
    const el = inputRef.current;
    if (!el) return;
    el.style.height = 'auto';
    el.style.height = `${Math.min(el.scrollHeight, MAX_INPUT_HEIGHT)}px`;
  };
  useEffect(() => {
    autoResize();
  }, [input]);

  /**
   * 断连恢复（业务错误分支用）：中止旧 SSE → 从后端拉取完整消息历史 → 刷新面试状态。
   * 与 scheduleReconnect 的区别：此处用于「业务错误（面试已结束 / 配额 / AI 异常）」
   * 而非网络断线；网络断线走自动重连，不弹阻断式报错。
   */
  const recoverFromDisconnect = useCallback(async () => {
    if (streamRef.current) {
      streamRef.current.abort();
      streamRef.current = null;
    }
    streamingRef.current = false;

    setSubmitting(false);
    setAiStarted(false);
    setStreamingId(null);

    if (!id) return;
    try {
      const [msgRes, intRes] = await Promise.all([
        interviewApi.getMessages(id),
        interviewApi.getById(id),
      ]);
      const msgs = extractArray(msgRes.data.data);
      if (msgs.length > 0) setMessages(sanitizeMessages(msgs));
      const raw = intRes.data.data;
      const iv = raw
        ? (((raw as unknown as { interview?: InterviewVO }).interview) ?? raw)
        : null;
      if (iv) {
        setInterview(iv as InterviewVO);
        if ((iv as InterviewVO).status === 'COMPLETED' || (iv as InterviewVO).status === 'TERMINATED') {
          setCompleted(true);
        }
      }
    } catch {
      /* 恢复拉取失败静默处理，不覆盖已有的错误提示 */
    }
  }, [id]);

  // 组件卸载时清理 SSE 与重连定时器
  useEffect(() => {
    return () => {
      streamRef.current?.abort();
      if (reconnectTimerRef.current !== null) clearTimeout(reconnectTimerRef.current);
      if (scorePollTimerRef.current !== null) clearInterval(scorePollTimerRef.current);
    };
  }, []);

  // 评分聚合轮询：面试已完成但分数未生成时，每 3 秒拉取一次面试状态
  const scorePollTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  useEffect(() => {
    if (!id || !interview || !over) return;
    if (interview.scoreTotal !== null) return;

    scorePollTimerRef.current = setInterval(async () => {
      try {
        const res = await interviewApi.getById(id);
        const raw = res.data.data;
        const iv = raw ? (((raw as unknown as { interview?: InterviewVO }).interview) ?? raw) : null;
        if (iv && (iv as InterviewVO).scoreTotal !== null) {
          setInterview(iv as InterviewVO);
          if (scorePollTimerRef.current !== null) clearInterval(scorePollTimerRef.current);
        }
      } catch {
        // 轮询失败静默处理，下一次继续
      }
    }, 3000);

    return () => {
      if (scorePollTimerRef.current !== null) clearInterval(scorePollTimerRef.current);
    };
  }, [id, interview, over]);

  // 注：不再监听 visibilitychange 主动关闭 SSE。
  // fetch-event-source 已设置 openWhenHidden:true（见 interview.ts），
  // 标签页隐藏时不断流、不重发；真正的断线由 onerror / onclose 检测，
  // 再由下方 scheduleReconnect 按指数退避自动重连。

  useEffect(() => {
    if (!id) return;
    const stateData = location.state as { interview?: InterviewVO; firstQuestion?: InterviewMessage } | undefined;
    if (stateData?.interview && String(stateData.interview.id) === id) {
      setInterview(stateData.interview);
      if (stateData.firstQuestion) {
        setMessages([{ ...stateData.firstQuestion, content: stripRoundCompleteToken(stateData.firstQuestion.content) }]);
      }
      setLoading(false);
      return;
    }
    Promise.allSettled([
      interviewApi.getById(id),
      interviewApi.getMessages(id),
    ]).then(([intRes, msgRes]) => {
      const raw = intRes.status === 'fulfilled' ? intRes.value.data.data : null;
      const iv = raw
        ? (((raw as unknown as { interview?: InterviewVO }).interview) ?? raw)
        : null;
      if (iv) {
        setInterview(iv as InterviewVO);
        if ((iv as InterviewVO).status === 'COMPLETED' || (iv as InterviewVO).status === 'TERMINATED') {
          setCompleted(true);
        }
      }
      if (msgRes.status === 'fulfilled') {
        const msgs = extractArray(msgRes.value.data.data);
        if (msgs.length > 0) setMessages(sanitizeMessages(msgs));
      }
      if (!iv && msgRes.status !== 'fulfilled') {
        setError('面试不存在');
      }
      setLoading(false);
    });
  }, [id]);

  // ---------- 断线重连与消息合并 ----------

  const clearReconnectTimer = () => {
    if (reconnectTimerRef.current !== null) {
      clearTimeout(reconnectTimerRef.current);
      reconnectTimerRef.current = null;
    }
  };

  /** 按 sequenceNum（缺失则 createdAt）升序排序 */
  const sortBySeq = (arr: InterviewMessage[]): InterviewMessage[] =>
    [...arr].sort((a, b) => {
      const sa = a.sequenceNum ?? 0;
      const sb = b.sequenceNum ?? 0;
      if (sa !== sb) return sa - sb;
      const ta = new Date(a.createdAt).getTime() || 0;
      const tb = new Date(b.createdAt).getTime() || 0;
      return ta - tb;
    });

  /**
   * 合并本地与后端消息：后端为权威，按 id 去重；同 sequenceNum 去重；
   * 保留未被后端覆盖的本地乐观消息（避免重连瞬间闪烁/丢字）。
   */
  const mergeMessages = (server: InterviewMessage[], local: InterviewMessage[]): InterviewMessage[] => {
    const byId = new Map<string, InterviewMessage>();
    const seenSeq = new Set<number>();
    for (const m of server) {
      const k = String(m.id);
      if (byId.has(k)) continue;
      if (m.sequenceNum && seenSeq.has(m.sequenceNum)) continue;
      byId.set(k, m);
      if (m.sequenceNum) seenSeq.add(m.sequenceNum);
    }
    for (const m of local) {
      const idStr = String(m.id);
      if (tempIdsRef.current.has(idStr) && !byId.has(idStr)) {
        byId.set(`local-${idStr}`, m);
      }
    }
    return sortBySeq([...byId.values()]);
  };

  /** 从后端拉取完整消息并与本地合并（去重），返回合并后的列表 */
  const fetchAndMergeMessages = async (): Promise<InterviewMessage[]> => {
    if (!id) return messagesRef.current;
    try {
      const res = await interviewApi.getMessages(id);
      const server = sanitizeMessages(extractArray(res.data.data));
      const merged = mergeMessages(server, messagesRef.current);
      setMessages(merged);
      return merged;
    } catch {
      return messagesRef.current;
    }
  };

  /** 重连成功：复位重连态、轻量提示「已恢复」，并再同步一次后端消息 */
  const finishReconnect = () => {
    clearReconnectTimer();
    reconnectingRef.current = false;
    reconnectAttemptsRef.current = 0;
    resubmittedRef.current = false;
    pendingSubmitRef.current = null;
    setConnBanner({ text: '连接已恢复，消息已同步', tone: 'recovered' });
    void fetchAndMergeMessages();
    setTimeout(() => {
      setConnBanner(prev => (prev && prev.tone === 'recovered' ? null : prev));
    }, 2500);
  };

  /** 重连耗尽（最多 3 次仍失败）：提示用户手动重试，不弹阻断式 alert */
  const showManualRetry = () => {
    clearReconnectTimer();
    reconnectingRef.current = false;
    setConnBanner({ text: '连接已断开，请点击重试', tone: 'failed' });
    streamingRef.current = false;
    streamRef.current?.abort();
    streamRef.current = null;
    setSubmitting(false);
  };

  /** 用户手动点击「重试」：重置计数后重新建立 SSE */
  const manualRetry = () => {
    reconnectAttemptsRef.current = 0;
    resubmittedRef.current = false;
    reconnectingRef.current = false;
    setConnBanner(null);
    const p = pendingSubmitRef.current;
    if (p) openStream(p, true);
  };

  /** 调度一次重连：记录断开、指数退避、最多 3 次，超过则转手动重试 */
  const scheduleReconnect = () => {
    if (overRef.current) return;
    if (reconnectingRef.current) return; // 同一次断开只调度一次
    if (reconnectAttemptsRef.current >= 3) {
      showManualRetry();
      return;
    }
    reconnectingRef.current = true;
    reconnectAttemptsRef.current += 1;
    setConnBanner({ text: '连接已断开，正在重连...', tone: 'reconnecting' });
    // 指数退避：800ms / 1600ms / 3200ms
    const delay = 800 * Math.pow(2, reconnectAttemptsRef.current - 1);
    clearReconnectTimer();
    reconnectTimerRef.current = setTimeout(() => {
      void doReconnect();
    }, delay);
  };

  /** 实际重连动作：先与后端同步判断真实状态，再决定是否需要重新生成 */
  const doReconnect = async () => {
    clearReconnectTimer();
    if (overRef.current) {
      reconnectingRef.current = false;
      return;
    }
    const payload = pendingSubmitRef.current;
    if (!payload) {
      finishReconnect();
      return;
    }
    const merged = await fetchAndMergeMessages();
    const last = merged[merged.length - 1];
    const lastIsUser = !!last && String(last.role).toUpperCase() === 'USER';
    if (!lastIsUser) {
      // 后端最新一条已是 AI 回复（SSE 中断前已落库）→ 视为恢复成功，无需重提
      finishReconnect();
      return;
    }
    if (resubmittedRef.current) {
      // 已尝试过重生成，避免堆叠重复消息，改由用户手动重试
      showManualRetry();
      return;
    }
    // 最后一条是 user 且无对应 AI 回复：之前的提交被 SSE 中断
    resubmittedRef.current = true;
    setConnBanner({ text: '消息已保存，正在重新生成...', tone: 'reconnecting' });
    openStream(payload, true);
  };

  /**
   * 建立一次 SSE 提交（提交回答 / 控制指令共用）。
   * @param isReconnect 为重连调用时，不重置提交态与重连计数，仅复用已保存的 pendingSubmit。
   */
  const openStream = (
    payload: { kind: 'answer'; content: string } | { kind: 'control'; command: ControlCommand },
    isReconnect = false,
  ) => {
    if (overRef.current || !id) return;
    // 新一次提交：清空流式光标锁定，避免残留光标落在上一条消息上
    setStreamingId(null);
    if (!isReconnect) {
      setSubmitting(true);
      setAiStarted(false);
      setError('');
      streamingRef.current = true;
      reconnectAttemptsRef.current = 0;
      resubmittedRef.current = false;
      reconnectingRef.current = false;
      setConnBanner(null);
    }
    pendingSubmitRef.current = payload;

    const tempId = payload.kind === 'answer' ? Date.now() : 0;
    if (payload.kind === 'answer') {
      const tempUser: InterviewMessage = {
        id: tempId,
        interviewId: id,
        role: 'USER',
        content: payload.content,
        questionType: null,
        sequenceNum: (messagesRef.current.length || 0) + 1,
        tokenCount: 0,
        createdAt: new Date().toISOString(),
      };
      tempIdsRef.current.add(String(tempId));
      setMessages(prev => [...prev, tempUser]);
    }

    // 流式状态（闭包内，跨帧累积）：
    let accumulated = '';                                   // 已收到的 chunk 文本累计
    let streamingAiIdLocal: string | number | null = null;  // 当前流式 AI 消息的 id（占位或真实）

    const handlers: SubmitAnswerHandlers = {
      // 流式增量帧：把后端推送的 chunk 文本逐帧追加到当前 AI 消息
      onChunk: (text) => {
        accumulated += text;
        setAiStarted(true);
        // 尾部保留策略：只渲染去掉末尾 STREAM_TAIL 的内容并清洗，避免标记闪现。
        const renderContent = streamRenderPrefix(accumulated);
        setMessages(prev => {
          const idx = prev.findIndex(m => m.id === streamingAiIdLocal);
          if (idx >= 0) {
            const copy = prev.slice();
            copy[idx] = { ...copy[idx], content: renderContent };
            return copy;
          }
          // 尚未有 AI 消息（chunk 先于 ai_response 到达）：创建占位 AI 消息
          const placeholderId =
            payload.kind === 'answer' ? `ai-streaming-${tempId}` : `ai-control-${Date.now()}`;
          streamingAiIdLocal = placeholderId;
          tempIdsRef.current.add(String(placeholderId));
          setStreamingId(placeholderId);
          const aiMsg: InterviewMessage = {
            id: placeholderId,
            interviewId: id,
            role: 'AI',
            content: renderContent,
            questionType: null,
            sequenceNum: (messagesRef.current.length || 0) + 2,
            tokenCount: 0,
            createdAt: new Date().toISOString(),
          };
          return [...prev, aiMsg];
        });
      },
      onMessage: (data) => {
        setAiStarted(true);
        // 优先用后端聚合帧里的完整 content；为空（部分实现仅以 chunk 流式）则回退到累计文本。
        const finalContent = stripRoundCompleteToken(data.aiResponse?.content || accumulated);
        const placeholderId = streamingAiIdLocal;
        setMessages(prev => {
          if (placeholderId !== null) {
            return prev.map(m =>
              m.id === placeholderId
                ? { ...data.aiResponse, content: finalContent }
                : payload.kind === 'answer' && m.id === tempId
                  ? data.userAnswer
                  : m,
            );
          }
          const base = payload.kind === 'answer'
            ? [...prev.filter(m => m.id !== tempId), data.userAnswer]
            : prev;
          return [...base, { ...data.aiResponse, content: finalContent }];
        });
        streamingAiIdLocal = data.aiResponse?.id;
        setStreamingId(data.aiResponse?.id ?? null);
        if (data.interview) setInterview(data.interview);
        if (data.interviewCompleted) setCompleted(true);

        // 收到终态帧：清除重连挂起态
        pendingSubmitRef.current = null;
        reconnectAttemptsRef.current = 0;
        if (reconnectingRef.current) finishReconnect();
      },
      onError: (err) => {
        const code = (err as Error & { code?: number }).code;
        if (code != null) {
          // 业务错误（面试已结束 / 配额 / AI 异常等）：提示并补拉状态
          if (code === INTERVIEW_ERROR_CODES.INTERVIEW_ALREADY_COMPLETED) {
            setError('本场面试已结束');
          } else {
            setError(err.message || '提交失败');
          }
          setTimeout(() => setError(''), 3000);
          pendingSubmitRef.current = null;
          recoverFromDisconnect();
          return;
        }
        // 网络/连接断开（无业务码）：不弹「连接失败」，转自动重连
        scheduleReconnect();
      },
      // 流结束但未收到终态帧（提交仍挂起）→ 视为异常断开，触发重连
      onClose: () => {
        if (pendingSubmitRef.current) scheduleReconnect();
      },
    };

    const stream = payload.kind === 'answer'
      ? interviewApi.submitAnswer(id, { content: payload.content }, handlers)
      : interviewApi.control(id, { command: payload.command }, handlers);

    streamRef.current = stream;

    void (async () => {
      try {
        // 兜底安全网：若 SSE 流迟迟不结束且从未收到任何 AI 帧，不让 await 永久挂起。
        await Promise.race([
          stream.done,
          new Promise<void>((resolve) =>
            setTimeout(() => {
              console.warn('[SSE interview] stream 未在 45s 内结束，将走兜底拉取');
              resolve();
            }, 45000),
          ),
        ]);
      } finally {
        // 若已被重连的新连接接管：不要终结旧连接的 UI 态
        if (streamRef.current !== stream) return;
        // 仍有未完成的提交（断线已触发重连）：交给重连逻辑处理，不在此收尾
        if (pendingSubmitRef.current) return;

        if (streamingAiIdLocal !== null && accumulated) {
          // 流结束（仅 chunk 流式到达）：把完整累积文本写入消息，释放尾部保留区
          const fullText = stripRoundCompleteToken(accumulated);
          setMessages(prev =>
            prev.map(m => (m.id === streamingAiIdLocal ? { ...m, content: fullText } : m)),
          );
        } else if (streamingAiIdLocal === null) {
          // 从未收到任何 AI 帧：主动拉取消息列表兜底，避免用户需手动刷新
          try {
            const res = await interviewApi.getMessages(id);
            const msgs = extractArray(res.data.data);
            if (msgs.length > 0) setMessages(sanitizeMessages(msgs));
          } catch { /* 忽略兜底失败，保留 onError 已设置的提示 */ }
        }
        streamingRef.current = false;
        streamRef.current = null;
        setSubmitting(false);
        setStreamingId(null);
      }
    })();
  };

  const handleSubmit = async (e?: FormEvent) => {
    e?.preventDefault();
    if (!input.trim() || submitting || over || !id) return;
    const userContent = input.trim();
    setInput('');
    openStream({ kind: 'answer', content: userContent }, false);
  };

  /**
   * 控制指令（跳过 / 提示 / 提前结束），对应文档 `POST /{id}/control`。
   * SSE 事件结构与 submitAnswer 相同（chunk + ai_response），但无用户回答消息。
   */
  const handleControl = async (command: ControlCommand) => {
    if (submitting || over || !id) return;
    openStream({ kind: 'control', command }, false);
  };

  const canSubmit = !over && !submitting && input.trim();

  const currentStageIdx = interview
    ? StageOrder.indexOf(interview.currentStage as InterviewStage)
    : -1;

  if (loading) return <div className="mx-auto max-w-7xl px-4 py-8"><div className="py-16 text-center text-muted-foreground">加载面试...</div></div>;
  if (error && !interview) return <div className="mx-auto max-w-7xl px-4 py-8"><div className="py-16 text-center text-muted-foreground">{error}</div></div>;

  return (
    <div className="flex flex-col" style={{ height: 'calc(100vh - 56px)' }}>
      {/* 连接状态提示（断线重连，非阻断式） */}
      {connBanner && (
        <div
          className={cn(
            'fixed left-1/2 top-14 z-[200] -translate-x-1/2 rounded-full px-5 py-2 text-sm shadow-lg',
            connBanner.tone === 'reconnecting'
              ? 'bg-amber-500 text-white'
              : connBanner.tone === 'recovered'
                ? 'bg-emerald-600 text-white'
                : 'bg-destructive text-white',
          )}
          role="status"
        >
          <span>{connBanner.text}</span>
          {connBanner.tone === 'failed' && (
            <button type="button" className="ml-3 font-medium underline" onClick={manualRetry}>
              重试
            </button>
          )}
        </div>
      )}

      {/* Interview Header */}
      <div className="border-b bg-background/90 px-6 py-3 backdrop-blur">
        <div className="mx-auto flex max-w-[780px] items-center justify-between">
          <div>
            <span className="text-base font-semibold">
              {interview?.companyDisplayName} · {interview?.positionDisplayName}
            </span>
            <span className="ml-3 text-sm text-muted-foreground">
              第 {interview?.totalQuestions} 题
            </span>
          </div>
          <div className="flex items-center gap-2">
            {!over && (
              <>
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => handleControl('HINT')}
                  disabled={submitting}
                >
                  提示
                </Button>
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => handleControl('SKIP')}
                  disabled={submitting}
                >
                  跳过
                </Button>
                <Button
                  variant="destructive"
                  size="sm"
                  onClick={() => {
                    if (confirm('确定结束面试？将生成总结与评分。')) handleControl('END');
                  }}
                >
                  结束面试
                </Button>
              </>
            )}
            <Button variant="ghost" size="sm" onClick={() => navigate('/interviews')}>
              返回
            </Button>
          </div>
        </div>
      </div>

      {/* Stage Indicator */}
      {interview && (
        <div className="flex items-center justify-center gap-1 bg-amber-50 px-4 py-3">
          {StageOrder.map((stage, i) => (
            <Fragment key={stage}>
              <div className="flex items-center gap-1">
                <span className={cn(
                  'flex size-5 items-center justify-center rounded-full text-[10px] font-semibold',
                  i < currentStageIdx ? 'bg-primary text-primary-foreground' : i === currentStageIdx ? 'bg-brand text-brand-foreground' : 'bg-muted text-muted-foreground',
                )}>
                  {i < currentStageIdx ? '✓' : ''}
                </span>
                <span className="text-xs text-muted-foreground">{InterviewStageMap[stage]}</span>
              </div>
              {i < StageOrder.length - 1 && <div className="h-px w-6 bg-border" />}
            </Fragment>
          ))}
        </div>
      )}

      {/* Messages */}
      <div className="flex-1 overflow-y-auto px-4 py-6">
        <div className="mx-auto flex max-w-[780px] flex-col gap-4">
          {messages.length === 0 && (
            <div className="py-16 text-center text-muted-foreground">暂无消息</div>
          )}

          {messages.map((msg) => {
            const isUser = msg.role.toLowerCase() === 'user';
            // 仅给「正在流式输出」的那一条 AI 消息加闪烁光标，用显式 streamingId 锁定，
            // 避免把光标误打到上一条已完成消息上造成闪烁
            const showCursor = !isUser && msg.id === streamingId;
            return (
              <div key={msg.id} className={cn('flex items-end gap-2', isUser ? 'flex-row-reverse' : 'flex-row')}>
                <div className={cn('flex size-8 shrink-0 items-center justify-center rounded-full text-xs font-medium', isUser ? 'bg-primary text-primary-foreground' : 'bg-muted text-muted-foreground')}>
                  {isUser ? '我' : 'AI'}
                </div>
                <div className={cn('max-w-[80%] rounded-2xl px-4 py-2 text-sm', isUser ? 'rounded-br-sm bg-primary text-primary-foreground' : 'rounded-bl-sm bg-muted text-foreground')}>
                  {msg.content}
                  {showCursor && (
                    <span
                      className="ml-0.5 inline-block h-3.5 w-1.5 translate-y-0.5 animate-pulse bg-current"
                      aria-hidden
                    />
                  )}
                </div>
              </div>
            );
          })}

          {submitting && !aiStarted && (
            <div className="flex items-end gap-2">
              <div className="flex size-8 shrink-0 items-center justify-center rounded-full bg-muted text-xs font-medium text-muted-foreground">AI</div>
              <div className="flex gap-1 rounded-2xl bg-muted px-4 py-3">
                <span className="size-2 animate-bounce rounded-full bg-muted-foreground/50 [animation-delay:-0.3s]" />
                <span className="size-2 animate-bounce rounded-full bg-muted-foreground/50 [animation-delay:-0.15s]" />
                <span className="size-2 animate-bounce rounded-full bg-muted-foreground/50" />
              </div>
            </div>
          )}

          {/* 结束卡片：面试结束后展示评分 / 建议 / 时长，并提供查看报告入口 */}
          {over && interview && (
            <div className="mt-8 rounded-2xl bg-amber-50 p-6 text-center">
              <h3 className="mb-4 text-base font-semibold">面试已结束</h3>
              {interview.scoreTotal !== null ? (
                <>
                  <div className="mb-4 text-5xl font-semibold text-brand">{interview.scoreTotal}</div>
                  <div className="grid grid-cols-5 gap-2">
                    {[
                      { v: interview.scoreTech, l: '技术深度' },
                      { v: interview.scoreExpression, l: '表达能力' },
                      { v: interview.scoreProject, l: '项目掌握' },
                      { v: interview.scoreLogic, l: '答题逻辑' },
                      { v: interview.scoreAdaptability, l: '应变能力' },
                    ].map(s => (
                      <div key={s.l} className="rounded-lg bg-background p-2">
                        <div className="text-lg font-semibold">{s.v ?? '-'}</div>
                        <div className="text-xs text-muted-foreground">{s.l}</div>
                      </div>
                    ))}
                  </div>
                </>
              ) : (
                <p className="mb-4 text-sm text-muted-foreground">评分计算中，请稍候...</p>
              )}
              {interview.suggestion && (
                <p className="mt-4 text-left text-sm text-muted-foreground">{interview.suggestion}</p>
              )}
              {interview.durationSeconds != null && (
                <p className="mt-2 text-sm text-muted-foreground">
                  面试时长：{formatDuration(interview.durationSeconds)}
                </p>
              )}
              <div className="mt-4 flex justify-center gap-3">
                <Button onClick={() => navigate('/reports', { state: { prefillInterviewId: String(interview.id) } })}>
                  查看报告
                </Button>
                <Button variant="outline" onClick={() => navigate('/interviews')}>返回列表</Button>
              </div>
            </div>
          )}

          <div ref={chatEndRef} />
        </div>
      </div>

      {/* Error toast（仅业务错误，连接断开走 connBanner 轻量提示） */}
      {error && (
        <div className="fixed left-1/2 top-14 z-[200] -translate-x-1/2 rounded-full bg-destructive px-5 py-2 text-sm text-white">
          {error}
        </div>
      )}

      {/* Input Bar */}
      {!over && (
        <div className="border-t bg-background p-4">
          <form className="mx-auto flex max-w-[780px] items-end gap-3" onSubmit={handleSubmit}>
            <Textarea
              ref={inputRef}
              value={input}
              onChange={e => setInput(e.target.value)}
              onKeyDown={e => {
                if (e.key === 'Enter' && !e.shiftKey) {
                  e.preventDefault();
                  handleSubmit();
                }
              }}
              placeholder="输入你的回答...（Enter 发送，Shift + Enter 换行）"
              disabled={submitting}
              rows={1}
              className="max-h-[200px] flex-1 resize-none"
            />
            <Button
              type="submit"
              disabled={!canSubmit}
              size="icon"
              className="size-12 shrink-0 rounded-full text-lg"
            >
              ↑
            </Button>
          </form>
        </div>
      )}
    </div>
  );
}
