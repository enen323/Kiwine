import { cn } from '@/lib/utils';
import { questionTypeLabel, questionDifficultyLabel } from '@/types';

/** 统一的标签外观（避免 shadcn Badge 默认 variant 的背景类与自定义色冲突）。 */
function Pill({
  className,
  title,
  children,
}: {
  className?: string;
  title?: string;
  children: React.ReactNode;
}) {
  return (
    <span
      title={title}
      className={cn(
        'inline-flex max-w-full items-center truncate rounded-full border px-2.5 py-0.5 text-xs font-medium',
        className,
      )}
    >
      {children}
    </span>
  );
}

/** 题型徽章：后端返回中文值直通显示；兼容英文枚举值（映射为中文）。 */
export function TypeBadge({ type }: { type: string }) {
  return (
    <Pill className="border-brand/30 bg-brand-muted text-brand">
      {questionTypeLabel(type)}
    </Pill>
  );
}

/** 难度徽章：中英文值兼容配色；文案原样/映射显示。 */
export function DifficultyBadge({ difficulty }: { difficulty: string }) {
  const d = questionDifficultyLabel(difficulty);
  const cls =
    d === '简单'
      ? 'border-emerald-500/30 bg-emerald-500/10 text-emerald-600 dark:text-emerald-400'
      : d === '中等'
        ? 'border-amber-500/30 bg-amber-500/10 text-amber-600 dark:text-amber-400'
        : 'border-rose-500/30 bg-rose-500/10 text-rose-600 dark:text-rose-400';
  return <Pill className={cls}>{d}</Pill>;
}

/**
 * 易错徽章：后端 easyMistake 实为「易错点描述」自由文本（非三档枚举）。
 * 空值或 'NORMAL' 不渲染；有描述则展示文本（超长截断，title 悬浮看全文）。
 */
export function MistakeBadge({ mistake }: { mistake: string | null | undefined }) {
  const text = mistake?.trim();
  if (!text || text === 'NORMAL') return null;
  return (
    <Pill
      title={text}
      className="max-w-[16rem] border-amber-500/30 bg-amber-500/10 text-amber-600 dark:text-amber-400"
    >
      易错：{text}
    </Pill>
  );
}
