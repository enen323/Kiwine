import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';

interface AiChatOptionProps {
  text: string;
  onClick: (text: string) => void;
  disabled?: boolean;
}

/** 提问卡内的单个选项：整行铺满、左对齐、文字可换行，点击立即发送对应文本。 */
export function AiChatOption({ text, onClick, disabled }: AiChatOptionProps) {
  return (
    <Button
      type="button"
      variant="outline"
      disabled={disabled}
      onClick={() => onClick(text)}
      title={text}
      className={cn(
        'h-auto w-full justify-start whitespace-normal break-words py-2 text-left',
        'cursor-pointer',
        disabled && 'cursor-not-allowed',
      )}
    >
      {text}
    </Button>
  );
}
