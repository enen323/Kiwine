package com.prepdeck.common.dto;

import java.math.BigDecimal;

public record QuotaCheckResult(boolean allowed, String type, String reason, BigDecimal price) {

    public static QuotaCheckResult allowed(String type) {
        return new QuotaCheckResult(true, type, null, null);
    }

    public static QuotaCheckResult denied(String reason, BigDecimal price) {
        return new QuotaCheckResult(false, null, reason, price);
    }
}
