import { useNavigate } from 'react-router-dom';
import { MessageSquarePlus } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { generateId } from '@/lib/utils';
import { RESUME_AI_SESSION_KEY } from './use-ai-chat';

interface AiChatFloatingButtonProps {
  resumeId: string;
}

/**
 * 编辑页右下角浮动入口：点击跳转到独立的 AI 对话页 `/resumes/:id/ai-chat`。
 * 跳转前确保该 resume 的 sessionId 已写入 localStorage（不存在则生成）。
 */
export function AiChatFloatingButton({ resumeId }: AiChatFloatingButtonProps) {
  const navigate = useNavigate();

  const handleClick = () => {
    const key = RESUME_AI_SESSION_KEY + resumeId;
    const existing = localStorage.getItem(key) ?? generateId();
    localStorage.setItem(key, existing);
    navigate(`/resumes/${resumeId}/ai-chat`);
  };

  return (
    <Button
      data-tour="editor-ai-chat"
      type="button"
      onClick={handleClick}
      className="fixed bottom-6 right-6 z-40 gap-2 rounded-full px-5 py-3 shadow-lg"
      aria-label="AI 帮你写简历"
    >
      <MessageSquarePlus className="size-5" />
      <span className="text-sm font-medium">AI 帮你写</span>
    </Button>
  );
}
