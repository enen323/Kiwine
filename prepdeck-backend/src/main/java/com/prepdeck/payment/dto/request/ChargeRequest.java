package com.prepdeck.payment.dto.request;

import jakarta.validation.constraints.NotNull;

public record ChargeRequest(@NotNull Long userId, @NotNull String serviceType) {
}
