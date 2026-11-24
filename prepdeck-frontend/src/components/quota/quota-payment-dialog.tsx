import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Loader2 } from 'lucide-react';
import { SERVICE_TYPE_LABELS } from '@/types';
import type { ServiceType } from '@/types';

export interface QuotaPaymentDialogProps {
  open: boolean;
  amount: number | null;
  serviceType: ServiceType | null;
  loading: boolean;
  error: string | null;
  onPay: () => void;
  onClose: () => void;
}

/**
 * 配额用尽后的付费弹窗：展示本次费用并调起微信支付。
 * 由 useQuotaGate 的 paymentDialog 状态驱动。
 */
export function QuotaPaymentDialog({
  open,
  amount,
  serviceType,
  loading,
  error,
  onPay,
  onClose,
}: QuotaPaymentDialogProps) {
  return (
    <Dialog
      open={open}
      onOpenChange={(v) => {
        if (!v) onClose();
      }}
    >
      <DialogContent showCloseButton={!loading}>
        <DialogHeader>
          <DialogTitle>免费额度已用完</DialogTitle>
          <DialogDescription>
            {serviceType ? SERVICE_TYPE_LABELS[serviceType] : '该服务'}
            的每月免费次数已用尽，可购买一次付费额度继续使用。
          </DialogDescription>
        </DialogHeader>

        <div className="flex items-baseline justify-between rounded-lg border bg-muted/40 px-4 py-3">
          <span className="text-sm text-muted-foreground">本次费用</span>
          <span className="text-2xl font-semibold text-foreground">
            ¥{amount != null ? amount.toFixed(2) : '—'}
          </span>
        </div>

        {error && <p className="text-sm text-destructive">{error}</p>}

        <DialogFooter className="sm:flex-col sm:items-stretch">
          <Button onClick={onPay} disabled={loading} className="w-full">
            {loading ? (
              <>
                <Loader2 className="mr-1.5 size-4 animate-spin" />
                支付中…
              </>
            ) : (
              '去支付'
            )}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
