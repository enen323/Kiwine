package com.prepdeck.payment.client;

import com.prepdeck.common.model.Result;
import com.prepdeck.quota.service.QuotaService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * 原 quota-service Feign 客户端，单体化后改为本地直调。
 *
 * <p>契约对照：QuotaController#grantPaidByUser（POST /api/v1/quota/grant-paid-by-user）。</p>
 */
@Component("paymentQuotaClient")
public class QuotaClient {

    @Resource
    private QuotaService quotaService;

    public Result<Void> grantPaid(Long userId, String serviceType) {
        quotaService.grantPaid(userId, serviceType);
        return Result.success();
    }
}
