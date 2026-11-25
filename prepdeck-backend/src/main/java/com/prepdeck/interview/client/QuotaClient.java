package com.prepdeck.interview.client;

import com.prepdeck.common.dto.QuotaCheckResult;
import com.prepdeck.common.model.Result;
import com.prepdeck.quota.service.QuotaService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * 原 quota-service Feign 客户端，单体化后改为本地直调。
 *
 * <p>契约对照：QuotaController#consume / #deductFree / #deductPaid。
 * QuotaService 返回 quota.dto.response.QuotaCheckResult，与 common.dto.QuotaCheckResult 结构相同但类型不同
 * （HTTP 时代靠 JSON 序列化互换，JVM 内必须显式转换），故此处逐字段转换以保持原方法签名不变。</p>
 */
@Component("interviewQuotaClient")
public class QuotaClient {

    @Resource
    private QuotaService quotaService;

    public Result<QuotaCheckResult> consume(String serviceType) {
        com.prepdeck.quota.dto.response.QuotaCheckResult result = quotaService.consume(serviceType);
        return Result.success(new QuotaCheckResult(result.allowed(), result.type(), result.reason(), result.price()));
    }

    public Result<Void> deductFree(String serviceType) {
        quotaService.deductFree(serviceType);
        return Result.success();
    }

    public Result<Void> deductPaid(String serviceType) {
        quotaService.deductPaid(serviceType);
        return Result.success();
    }
}
