package com.prepdeck.payment.client;

import com.prepdeck.common.enums.ErrorCode;
import com.prepdeck.common.exception.BadRequestException;
import com.prepdeck.common.exception.BusinessException;
import com.prepdeck.common.model.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class QuotaClientFallbackFactory implements FallbackFactory<QuotaClient> {

    @Override
    public QuotaClient create(Throwable cause) {
        if (cause instanceof BusinessException) {
            throw (BusinessException) cause;
        }
        log.error("QuotaClient 调用失败，触发熔断降级", cause);
        return (userId, serviceType) -> {
            throw new BadRequestException(ErrorCode.SERVICE_UNAVAILABLE);
        };
    }
}
