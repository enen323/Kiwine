package com.prepdeck.quota.controller;

import com.prepdeck.common.model.Result;
import com.prepdeck.common.util.SecurityContext;
import com.prepdeck.quota.dto.response.QuotaCheckResult;
import com.prepdeck.quota.dto.response.QuotaVO;
import com.prepdeck.quota.service.QuotaService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/quota")
@RequiredArgsConstructor
public class QuotaController {

    private final QuotaService quotaService;

    @GetMapping("/health")
    public Result<String> health() {
        return Result.success("quota-service is running");
    }

    @PostMapping("/consume")
    public Result<QuotaCheckResult> consume(@RequestParam String serviceType) {
        return Result.success(quotaService.consume(serviceType));
    }

    @PostMapping("/deduct-free")
    public Result<Void> deductFree(@RequestParam String serviceType) {
        quotaService.deductFree(serviceType);
        return Result.success();
    }

    @PostMapping("/deduct-paid")
    public Result<Void> deductPaid(@RequestParam String serviceType) {
        quotaService.deductPaid(serviceType);
        return Result.success();
    }

    @PostMapping("/grant-paid")
    public Result<Void> grantPaid(@RequestParam String serviceType) {
        quotaService.grantPaid(serviceType);
        return Result.success();
    }

    /**
     * 供内部服务（如 payment-service 微信回调）调用，直接传入 userId，
     * 不依赖 SecurityContext / X-User-Id 头。
     */
    @PostMapping("/grant-paid-by-user")
    public Result<Void> grantPaidByUser(@RequestParam Long userId, @RequestParam String serviceType) {
        quotaService.grantPaid(userId, serviceType);
        return Result.success();
    }

    @GetMapping
    public Result<QuotaVO> getQuota() {
        return Result.success(quotaService.getQuota());
    }

    @PostMapping("/reset")
    public Result<Void> resetMonthly() {
        quotaService.resetMonthly();
        return Result.success();
    }
}
