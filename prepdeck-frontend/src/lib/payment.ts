import type { ChargePaymentResponse } from '@/types';

/**
 * 微信 JSSDK 的最小类型声明（仅声明支付所需部分）。
 * 实际运行时由微信客户端注入 `window.wx`。
 */
interface WechatJSSDK {
  requestPayment: (options: {
    timeStamp: string;
    nonceStr: string;
    /** 注意与 JS 关键字冲突，JSSDK 字段名为 `package` */
    package: string;
    signType: string;
    paySign: string;
    success?: () => void;
    fail?: (err: { errMsg?: string }) => void;
    complete?: () => void;
  }) => void;
}

declare global {
  interface Window {
    wx?: WechatJSSDK;
  }
}

/**
 * 调起微信支付。仅在微信内置浏览器（已注入 JSSDK）中可用。
 * 非微信环境或未注入 JSSDK 时抛出明确错误，由调用方提示用户。
 *
 * @param params POST /payment/charge 返回的完整参数（含 packageValue / paySign 等）
 * @returns 支付成功 resolve；用户取消或失败 reject
 */
export function requestWechatPayment(params: ChargePaymentResponse): Promise<void> {
  return new Promise((resolve, reject) => {
    const wx = typeof window !== 'undefined' ? window.wx : undefined;
    if (!wx || typeof wx.requestPayment !== 'function') {
      reject(new Error('当前环境不支持微信支付，请在微信客户端中打开'));
      return;
    }
    wx.requestPayment({
      timeStamp: params.timeStamp,
      nonceStr: params.nonceStr,
      // 后端字段为 packageValue，对应 JSSDK 的 `package`
      package: params.packageValue,
      signType: params.signType,
      paySign: params.paySign,
      success: () => resolve(),
      fail: (err) => reject(new Error(err?.errMsg || '微信支付失败，请重试')),
    });
  });
}
