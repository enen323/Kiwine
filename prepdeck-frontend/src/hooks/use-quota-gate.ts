import { useCallback, useRef, useState } from 'react';
import { quotaApi } from '@/api/quota';
import { paymentApi } from '@/api/payment';
import { requestWechatPayment } from '@/lib/payment';
import { toast } from 'sonner';
import type { ServiceType } from '@/types';

/** 支付弹窗的受控状态 */
export interface PaymentDialogState {
  open: boolean;
  amount: number | null;
  serviceType: ServiceType | null;
  /** 正在发起支付 / 等待微信回调 */
  loading: boolean;
  /** 支付失败或环境不支持时的错误提示 */
  error: string | null;
}

const CLOSED_DIALOG: PaymentDialogState = {
  open: false,
  amount: null,
  serviceType: null,
  loading: false,
  error: null,
};

export interface QuotaGateResult {
  /** 正在检查配额（仅 consume 阶段，不含支付等待） */
  checking: boolean;
  /**
   * 请求使用一次目标服务。内部流程：
   * 1. 调用 /quota/consume（检查并扣减，后端自动完成扣减）
   * 2. allowed=true → 后端已扣减，直接执行 action
   * 3. allowed=false 且有 price → 弹出支付窗，支付成功后重新走 1-2（此时应命中付费额度）
   * 4. allowed=false 且无 price → 提示配额用尽
   */
  request: (action: () => Promise<void> | void) => Promise<void>;
  /** 传给 <QuotaPaymentDialog /> 的受控属性 */
  paymentDialog: PaymentDialogState & {
    onPay: () => void;
    onClose: () => void;
  };
}

/**
 * 通用配额闸：将一个「消耗一次配额的服务动作」包裹为受配额/支付约束的调用。
 *
 * @param serviceType 服务类型（INTERVIEW / RESUME_CHAT）
 * @param userId 当前用户 ID；为 null 时 request 直接提示重新登录，不发起请求
 */
export function useQuotaGate(
  serviceType: ServiceType,
  userId: string | number | null,
): QuotaGateResult {
  const [checking, setChecking] = useState(false);
  const [dialog, setDialog] = useState<PaymentDialogState>(CLOSED_DIALOG);
  const pendingActionRef = useRef<(() => Promise<void> | void) | null>(null);

  const request = useCallback(
    async (action: () => Promise<void> | void) => {
      if (userId == null) {
        toast.error('未获取到用户身份，请重新登录');
        return;
      }
      setChecking(true);
      try {
        const res = await quotaApi.consume({ userId, serviceType });
        const apiRes = res.data;
        // 业务错误：HTTP 200 但 code != 0
        // （例如 { code: 70000, message: '本月免费额度已用完', data: null }）
        if (apiRes.code !== 0) {
          throw new Error(apiRes.message || '配额校验失败');
        }
        const data = apiRes.data;
        if (!data) throw new Error('配额校验失败');

        if (data.allowed) {
          // 后端已在 consume 中自动完成扣减，直接执行目标动作
          await action();
          return;
        }

        // 配额耗尽：仍可付费则弹支付窗，等待用户支付后再重新走配额闸
        if (data.price != null) {
          pendingActionRef.current = action;
          setDialog({
            open: true,
            amount: data.price,
            serviceType,
            loading: false,
            error: null,
          });
          return;
        }
        toast.error('本服务配额已用尽');
      } catch (err) {
        // 优先从 axios 错误响应体中提取后端 message（非 200 HTTP 状态时）
        const axiosMsg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
        const msg = axiosMsg || (err instanceof Error ? err.message : '配额校验失败，请稍后重试');
        toast.error(msg);
      } finally {
        setChecking(false);
      }
    },
    [userId, serviceType],
  );

  /** 用户在支付窗点击「去支付」 */
  const handlePay = useCallback(async () => {
    if (userId == null || !pendingActionRef.current) return;
    const action = pendingActionRef.current;
    setDialog((d) => ({ ...d, loading: true, error: null }));
    try {
      const chargeRes = await paymentApi.charge({ userId, serviceType });
      const payParams = chargeRes.data.data;
      if (!payParams) throw new Error('获取支付参数失败');

      await requestWechatPayment(payParams);

      // 支付成功：后端回调已增加一次付费配额，关闭弹窗并重新走配额闸
      // （此时 consume 应命中付费额度 → 自动扣减 → 执行 action）
      setDialog(CLOSED_DIALOG);
      pendingActionRef.current = null;
      toast.success('支付成功');
      await request(action);
    } catch (err) {
      const msg = err instanceof Error ? err.message : '支付未完成';
      setDialog((d) => ({ ...d, loading: false, error: msg }));
    }
  }, [userId, serviceType, request]);

  const closeDialog = useCallback(() => {
    setDialog(CLOSED_DIALOG);
    pendingActionRef.current = null;
  }, []);

  return {
    checking,
    request,
    paymentDialog: {
      ...dialog,
      onPay: handlePay,
      onClose: closeDialog,
    },
  };
}
