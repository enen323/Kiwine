package com.prepdeck.job.client;

import com.prepdeck.common.enums.ErrorCode;
import com.prepdeck.common.exception.BadRequestException;
import com.prepdeck.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ResumeClientFallbackFactory implements FallbackFactory<ResumeClient> {

    @Override
    public ResumeClient create(Throwable cause) {
        if (cause instanceof BusinessException) {
            throw (BusinessException) cause;
        }
        log.error("ResumeClient 调用失败，触发熔断降级", cause);
        return id -> {
            throw new BadRequestException(ErrorCode.SERVICE_UNAVAILABLE);
        };
    }
}
