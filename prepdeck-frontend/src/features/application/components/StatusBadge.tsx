import { Badge } from '@/components/ui/badge';
import { STATUS_BADGE_CLASS, STATUS_LABELS } from '../types';
import type { ApplicationStatus } from '../types';
import { cn } from '@/lib/utils';

export function StatusBadge({
  status,
  className,
}: {
  status: ApplicationStatus;
  className?: string;
}) {
  return (
    <Badge variant="outline" className={cn('font-medium', STATUS_BADGE_CLASS[status], className)}>
      {STATUS_LABELS[status]}
    </Badge>
  );
}
