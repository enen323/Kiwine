import { cn } from '@/lib/utils';
import type { ChatMessage } from '@/types';

interface AiChatMessageProps {
  message: ChatMessage;
}

/** 单条消息气泡：user 右侧品牌色，assistant 左侧 muted。
 *  选项由输入区「提问卡」统一呈现（对齐原交互），聊天流仅展示问答文本。 */
export function AiChatMessage({ message }: AiChatMessageProps) {
  const isUser = message.role === 'user';

  return (
    <div className={cn('flex flex-col gap-2', isUser ? 'items-end' : 'items-start')}>
      <div
        className={cn(
          'max-w-[80%] whitespace-pre-wrap break-words rounded-2xl px-3 py-2 text-sm leading-relaxed',
          isUser
            ? 'bg-brand text-brand-foreground rounded-br-sm'
            : 'bg-muted text-foreground rounded-bl-sm',
        )}
      >
        {message.content}
      </div>
    </div>
  );
}
