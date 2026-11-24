package com.prepdeck.payment.dto.response;

import java.math.BigDecimal;

public record ChargeResponse(
        String appId,
        String timeStamp,
        String nonceStr,
        String packageValue,
        String signType,
        String paySign,
        BigDecimal amount,
        String serviceType
) {
}
