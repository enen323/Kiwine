package com.prepdeck.job.client;

import com.prepdeck.common.dto.QuotaCheckResult;
import com.prepdeck.common.model.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "quota-service", path = "/api/v1/quota", fallbackFactory = QuotaClientFallbackFactory.class)
public interface QuotaClient {

    @PostMapping("/consume")
    Result<QuotaCheckResult> consume(@RequestParam Long userId, @RequestParam String serviceType);
}
