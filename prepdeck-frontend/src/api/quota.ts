import request from './request';
import type {
  ApiResponse,
  ServiceType,
  ConsumeQuotaRequest,
  QuotaCheckResult,
  UserQuotaVO,
} from '../types';

/** 扣除/增加配额所需的 query 参数（userId + serviceType） */
export interface QuotaQuery {
  userId: string | number;
  serviceType: ServiceType;
}

export const quotaApi = {
  /**
   * 消费配额（检查并扣减）：优先免费额度（每月 3 次），免费用完后检查付费额度。
   * allowed=true 时后端已自动完成扣减，前端无需再调用 deduct-free / deduct-paid。
   * allowed=false 时据 price 决定是否调起支付。
   */
  consume: (data: ConsumeQuotaRequest) =>
    request.post<ApiResponse<QuotaCheckResult>>('/quota/consume', null, {
      params: { userId: data.userId, serviceType: data.serviceType },
    }),

  /** 扣减免费额度（内部接口，通常由业务服务在确认服务执行成功后调用）。query 参数。 */
  deductFree: (params: QuotaQuery) =>
    request.post<ApiResponse<null>>('/quota/deduct-free', null, {
      params: { userId: params.userId, serviceType: params.serviceType },
    }),

  /** 扣减付费额度（内部接口，用于扣减用户已购买的付费次数）。query 参数。 */
  deductPaid: (params: QuotaQuery) =>
    request.post<ApiResponse<null>>('/quota/deduct-paid', null, {
      params: { userId: params.userId, serviceType: params.serviceType },
    }),

  /**
   * 增加付费配额（支付成功后由 payment-service 自动调用），前端一般无需调用。
   * 保留以便需要时手动补偿。
   */
  grantPaid: (params: QuotaQuery) =>
    request.post<ApiResponse<null>>('/quota/grant-paid', null, {
      params: { userId: params.userId, serviceType: params.serviceType },
    }),

  /** 获取用户配额（免费额度每月 1 日 0 点重置）。 */
  getQuota: (userId: string | number) =>
    request.get<ApiResponse<UserQuotaVO>>(`/quota/${userId}`),

  /** 重置全量用户免费额度（管理员接口，通常由定时任务执行）。 */
  reset: () =>
    request.post<ApiResponse<null>>('/quota/reset'),
};
