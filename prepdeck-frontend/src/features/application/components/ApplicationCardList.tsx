import { Separator } from '@/components/ui/separator';
import { StatusBadge } from './StatusBadge';
import { ApplicationRowActions } from './ApplicationRowActions';
import { formatDateTime } from '../utils';
import type { JobApplication } from '../types';

interface ApplicationCardListProps {
  items: JobApplication[];
  loading: boolean;
  onView: (a: JobApplication) => void;
  onEdit: (a: JobApplication) => void;
  onDelete: (a: JobApplication) => void;
}

const DASH = <span className="text-muted-foreground/60">—</span>;

/** 移动端卡片列表（<768px 显示）。 */
export function ApplicationCardList({ items, loading, onView, onEdit, onDelete }: ApplicationCardListProps) {
  if (loading) {
    return <div className="py-6 text-center text-sm text-muted-foreground">加载中...</div>;
  }
  if (items.length === 0) {
    return <div className="py-6 text-center text-sm text-muted-foreground">暂无投递记录</div>;
  }
  return (
    <div className="space-y-3">
      {items.map((a) => (
        <div key={a.id} className="rounded-[10px] border bg-card p-4">
          <div className="flex items-start justify-between gap-2">
            <div className="min-w-0 truncate font-semibold text-foreground">{a.company}</div>
            <StatusBadge status={a.status} />
          </div>
          <div className="mt-0.5 truncate text-[13px] text-muted-foreground">{a.position}</div>
          <div className="mt-1 text-xs text-muted-foreground">
            {a.channel ? a.channel : DASH}
            <span className="mx-1.5">·</span>
            {formatDateTime(a.appliedAt) || DASH}
          </div>
          <Separator className="my-3" />
          <ApplicationRowActions
            application={a}
            onView={onView}
            onEdit={onEdit}
            onDelete={onDelete}
            align="start"
          />
        </div>
      ))}
    </div>
  );
}
