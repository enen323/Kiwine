import request from './request';
import type {
  ApiResponse,
  ChargePaymentRequest,
  ChargePaymentResponse,
} from '../types';

export const paymentApi = {
  /**
   * 发起支付，返回微信 JSSDK 调起参数（wx.requestPayment）。
   * 支付成功后系统自动为用户增加一次付费配额（见 /payment/callback/wechat）。
   */
  charge: (data: ChargePaymentRequest) =>
    request.post<ApiResponse<ChargePaymentResponse>>('/payment/charge', data),
};
