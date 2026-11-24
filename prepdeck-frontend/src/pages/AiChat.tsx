import { useCallback, useEffect, useRef, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { AlertCircle, ArrowLeft, Loader2, Sparkles } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { ScrollArea } from '@/components/ui/scroll-area';
import { resumeApi } from '@/api/resume';
import { generateId } from '@/lib/utils';
import type { CreateResumeRequest } from '@/types';
import { useAiChat, RESUME_AI_SESSION_KEY } from '@/components/editor/ai-chat/use-ai-chat';
import { AiChatMessage } from '@/components/editor/ai-chat/ai-chat-message';
import { AiChatInput } from '@/components/editor/ai-chat/ai-chat-input';

/* =========================================================================
 * 独立 AI 对话式简历生成页 —— 由编辑页「AI 帮你写」入口跳转而来
 * 路由：/resumes/:id/ai-chat
 * 全屏沉浸式对话：AI 自由追问挖掘用户经历，对话结束后一键生成简历。
 * 配额检查由后端在 ai-chat 请求内部完成，前端无需单独调用 /quota/consume。
 * 复用 editor/ai-chat 下的 Hook 与消息 / 输入组件，仅重新编排为整页布局。
 * ========================================================================= */
export default function AiChat() {
  const { id } = useParams();
  const navigate = useNavigate();
  const resumeId = id ?? '';

  // sessionId：按 resumeId 隔离，读取 localStorage（不存在则生成并写入）
  const [sessionId] = useState<string>(() => {
    const key = RESUME_AI_SESSION_KEY + resumeId;
    const existing = localStorage.getItem(key) ?? generateId();
    localStorage.setItem(key, existing);
    return existing;
  });

  const {
    messages,
    isConnecting,
    isComplete,
    result,
    error,
    pendingQuestion,
    sendMessage,
    retry,
  } = useAiChat(sessionId);

  const [confirmError, setConfirmError] = useState<string | null>(null);
  const [confirming, setConfirming] = useState(false);
  const bottomRef = useRef<HTMLDivElement>(null);

  // 新消息 / 加载态 / 提问卡变化自动滚动到底部
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, isConnecting, pendingQuestion]);

  const handleBack = useCallback(() => {
    if (resumeId) navigate(`/resumes/${resumeId}`);
    else navigate('/resumes');
  }, [resumeId, navigate]);

  const handleConfirm = async () => {
    setConfirming(true);
    setConfirmError(null);
    try {
      // type === 'complete' 时 result 已含生成好的简历正文，直接采用，无需再轮询结果接口。
      const finalResult = result;
      if (!finalResult || !finalResult.title) {
        setConfirmError('未获取到简历内容，请重新与 AI 对话');
        setConfirming(false);
        return;
      }
      // AI 返回的简历里 themeConfig 可能为 null。若原样透传给 create，后端会
      // 存进一条 themeConfig=null 的记录，后续渲染/导出读到空主题会异常。
      // 这里把 null/undefined 的主题剔除，交由后端使用默认主题。
      const rawResult = finalResult as unknown as Record<string, unknown>;
      if (rawResult.themeConfig == null) delete rawResult.themeConfig;
      const res = await resumeApi.create({
        ...(rawResult as unknown as Omit<CreateResumeRequest, 'sessionId'>),
        sessionId,
      });
      // create 已返回完整 ResumeVO；直接随路由传给编辑页，
      // 避免「刚创建就再 GET 一次」因瞬时 404 / 最终一致性而渲染失败。
      const created = res.data?.data ?? null;
      const newId = created?.id;
      navigate(
        newId ? `/resumes/${newId}` : '/resumes',
        { state: newId ? { resume: created, fromAiChat: true } : undefined },
      );
    } catch {
      setConfirmError('生成简历失败，请稍后重试');
      setConfirming(false);
    }
  };

  const headerTitle = isComplete
    ? '简历已生成'
    : isConnecting
      ? 'AI 思考中…'
      : 'AI 帮你写简历';

  const inputDisabled = isConnecting || isComplete || !!error;

  return (
    <div className="flex h-screen flex-col overflow-hidden bg-background text-foreground">
      {/* 顶部栏：返回 + 标题 */}
      <header className="flex items-center gap-3 border-b px-4 py-3">
        <Button
          type="button"
          variant="ghost"
          size="icon"
          onClick={handleBack}
          aria-label="返回编辑器"
        >
          <ArrowLeft className="size-5" />
        </Button>
        <div className="flex items-center gap-2">
          <Sparkles className="size-4 text-brand" />
          <span className="text-sm font-medium">{headerTitle}</span>
        </div>
      </header>

      {/* 消息区：居中列，最大宽度约束以便阅读 */}
      <div className="min-h-0 flex-1">
        <ScrollArea className="h-full">
          <div className="mx-auto flex w-full max-w-2xl flex-col gap-4 px-4 py-6">
            {error ? (
              <ErrorBanner message={error} onRetry={retry} />
            ) : messages.length === 0 ? (
              <EmptyState onStart={sendMessage} />
            ) : (
              messages.map((m, i) => <AiChatMessage key={i} message={m} />)
            )}

            {isConnecting && !isComplete && <ThinkingSkeleton />}
            <div ref={bottomRef} />
          </div>
        </ScrollArea>
      </div>

      {/* 底部：确认生成 / 输入框 */}
      <div className="border-t bg-background px-4 py-3">
        <div className="mx-auto w-full max-w-2xl">
          {isComplete ? (
            <div className="flex flex-col gap-2">
              {confirmError && (
                <p className="text-xs text-destructive">{confirmError}</p>
              )}
              <Button
                className="w-full"
                onClick={handleConfirm}
                disabled={confirming}
              >
                {confirming ? (
                  <>
                    <Loader2 className="mr-1.5 size-4 animate-spin" />
                    生成中…
                  </>
                ) : (
                  '确认生成简历'
                )}
              </Button>
            </div>
          ) : (
            <AiChatInput
              onSend={sendMessage}
              disabled={inputDisabled}
              isConnecting={isConnecting}
              pendingQuestion={pendingQuestion}
              onOptionClick={sendMessage}
            />
          )}
        </div>
      </div>
    </div>
  );
}

/** 空态：引导用户开始对话，并提供一个快捷起手输入。 */
function EmptyState({ onStart }: { onStart: (text: string) => void }) {
  const starters = [
    '我想做一份前端开发简历',
    '帮我挖掘我的项目经历',
    '我是应届生，没什么经验',
  ];
  return (
    <div className="flex flex-col items-center justify-center gap-4 py-16 text-center">
      <div className="flex size-14 items-center justify-center rounded-2xl bg-brand-muted/40">
        <Sparkles className="size-7 text-brand" />
      </div>
      <div className="space-y-1">
        <p className="text-base font-medium text-foreground">
          开始和 AI 对话，帮你挖掘经历生成简历
        </p>
        <p className="text-sm text-muted-foreground">
          AI 会通过提问逐步了解你的背景，你也可以直接从下面开始。
        </p>
      </div>
      <div className="flex flex-wrap justify-center gap-2">
        {starters.map((s) => (
          <Button
            key={s}
            type="button"
            variant="outline"
            size="sm"
            className="cursor-pointer"
            onClick={() => onStart(s)}
          >
            {s}
          </Button>
        ))}
      </div>
    </div>
  );
}

function ThinkingSkeleton() {
  return (
    <div className="flex items-start gap-2">
      <div className="max-w-[80%] rounded-2xl rounded-bl-sm bg-muted px-3 py-3">
        <div className="flex gap-1.5">
          <span className="size-2 animate-bounce rounded-full bg-muted-foreground/50 [animation-delay:-0.3s]" />
          <span className="size-2 animate-bounce rounded-full bg-muted-foreground/50 [animation-delay:-0.15s]" />
          <span className="size-2 animate-bounce rounded-full bg-muted-foreground/50" />
        </div>
      </div>
    </div>
  );
}

function ErrorBanner({
  message,
  onRetry,
}: {
  message: string;
  onRetry: () => void;
}) {
  return (
    <div className="flex flex-col gap-3 rounded-lg border border-destructive/40 bg-destructive/10 px-4 py-3">
      <div className="flex items-center gap-2 text-sm text-destructive">
        <AlertCircle className="size-4" />
        <span>{message}</span>
      </div>
      <Button variant="outline" size="sm" onClick={onRetry} className="self-start">
        重试
      </Button>
    </div>
  );
}
