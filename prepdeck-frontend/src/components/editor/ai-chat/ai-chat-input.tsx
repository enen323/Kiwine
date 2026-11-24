import { useState, type KeyboardEvent } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Loader2, Send } from 'lucide-react';
import type { PendingQuestion } from './use-ai-chat';
import { AiChatOption } from './ai-chat-option';

interface AiChatInputProps {
  onSend: (text: string) => void;
  /** 当前是否禁止发送（AI 回复中或对话已完成） */
  disabled?: boolean;
  isConnecting?: boolean;
  /** AI 抛出的待回答问题 + 选项，渲染为输入区上方的「提问卡」。 */
  pendingQuestion?: PendingQuestion | null;
  /** 点击选项的回调（直接当作一条用户消息发送） */
  onOptionClick: (text: string) => void;
}

/** 底部输入区：AI 提问时，在输入框上方弹出「提问卡」（问题 + 快捷选项），
 *  同时保留自由输入框，用户也可输入自定义回答。对齐 Claude Code / OpenCode 交互。 */
export function AiChatInput({
  onSend,
  disabled,
  isConnecting,
  pendingQuestion,
  onOptionClick,
}: AiChatInputProps) {
  const [text, setText] = useState('');

  const handleSend = () => {
    const trimmed = text.trim();
    if (!trimmed || disabled) return;
    onSend(trimmed);
    setText('');
  };

  const handleKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  return (
    <div className="flex flex-col gap-2">
      {pendingQuestion && (
        <div className="rounded-xl border border-border bg-muted/40 p-3">
          <p className="mb-2.5 text-sm font-medium text-foreground">
            {pendingQuestion.content}
          </p>
          <div className="flex flex-col gap-2">
            {pendingQuestion.options.map((opt, i) => (
              <AiChatOption
                key={`${opt}-${i}`}
                text={opt}
                onClick={onOptionClick}
                disabled={disabled}
              />
            ))}
          </div>
          <p className="mt-2 text-xs text-muted-foreground">
            点选以上选项，或直接在下方输入你的回答
          </p>
        </div>
      )}

      <div className="flex items-center gap-2">
        <Input
          value={text}
          onChange={(e) => setText(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="输入你的回答…"
          disabled={disabled}
        />
        <Button
          type="button"
          onClick={handleSend}
          disabled={disabled || !text.trim()}
          size="icon"
          aria-label="发送"
        >
          {isConnecting ? (
            <Loader2 className="size-4 animate-spin" />
          ) : (
            <Send className="size-4" />
          )}
        </Button>
      </div>
    </div>
  );
}
