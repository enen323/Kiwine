package com.prepdeck.payment.client;

import com.prepdeck.common.model.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "quota-service", path = "/api/v1/quota", fallbackFactory = QuotaClientFallbackFactory.class)
public interface QuotaClient {

    @PostMapping("/grant-paid-by-user")
    Result<Void> grantPaid(@RequestParam Long userId, @RequestParam String serviceType);
}
