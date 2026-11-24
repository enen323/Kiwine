import { useEffect, useState } from 'react';
import { quotaApi } from '@/api/quota';
import { Skeleton } from '@/components/ui/skeleton';
import { FREE_QUOTA_PER_MONTH, SERVICE_TYPE_LABELS } from '@/types';
import type { ServiceType, UserQuotaVO } from '@/types';

/**
 * 我的配额卡片：展示两类服务的免费剩余次数（每月重置）与付费额度剩余次数。
 * 用于个人中心（/profile）。
 */
export function QuotaSummary({ userId }: { userId: string | number }) {
  const [loading, setLoading] = useState(true);
  const [quota, setQuota] = useState<UserQuotaVO | null>(null);

  useEffect(() => {
    let active = true;
    quotaApi
      .getQuota(userId)
      .then((res) => {
        if (active) setQuota(res.data.data);
      })
      .catch(() => {})
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [userId]);

  if (loading) {
    return (
      <div className="space-y-3">
        <Skeleton className="h-5 w-32" />
        <Skeleton className="h-16 w-full" />
        <Skeleton className="h-16 w-full" />
      </div>
    );
  }

  if (!quota) {
    return (
      <p className="text-sm text-muted-foreground">暂无配额信息</p>
    );
  }

  const rows: { type: ServiceType; freeUsed: number; paid: number }[] = [
    {
      type: 'INTERVIEW',
      freeUsed: quota.interviewFreeUsed,
      paid: quota.interviewPaid,
    },
    {
      type: 'RESUME_CHAT',
      freeUsed: quota.resumeChatFreeUsed,
      paid: quota.resumeChatPaid,
    },
  ];

  return (
    <div className="space-y-3">
      {rows.map((r) => {
        const remaining = Math.max(0, FREE_QUOTA_PER_MONTH - r.freeUsed);
        const pct = (remaining / FREE_QUOTA_PER_MONTH) * 100;
        return (
          <div key={r.type} className="rounded-lg border px-4 py-3">
            <div className="flex items-center justify-between">
              <span className="text-sm font-medium">
                {SERVICE_TYPE_LABELS[r.type]}
              </span>
              <span className="text-xs text-muted-foreground">
                付费额度剩余 {r.paid} 次
              </span>
            </div>
            <div className="mt-2 flex items-center gap-2">
              <div className="h-2 flex-1 overflow-hidden rounded-full bg-muted">
                <div
                  className="h-full rounded-full bg-brand transition-all"
                  style={{ width: `${pct}%` }}
                />
              </div>
              <span className="whitespace-nowrap text-xs text-muted-foreground">
                免费剩余 {remaining}/{FREE_QUOTA_PER_MONTH}
              </span>
            </div>
          </div>
        );
      })}
    </div>
  );
}
