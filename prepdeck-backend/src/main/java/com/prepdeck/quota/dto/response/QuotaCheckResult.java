package com.prepdeck.quota.dto.response;

public record QuotaCheckResult(boolean allowed, String type, String reason, java.math.BigDecimal price) {

    public static QuotaCheckResult allowed(String type) {
        return new QuotaCheckResult(true, type, null, null);
    }

    public static QuotaCheckResult denied(String reason, java.math.BigDecimal price) {
        return new QuotaCheckResult(false, null, reason, price);
    }
}
