package com.prepdeck.interview.client;

import com.prepdeck.common.dto.UserPracticeSummaryVO;
import com.prepdeck.common.enums.ErrorCode;
import com.prepdeck.common.exception.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class QuestionClientFallbackFactory implements FallbackFactory<QuestionClient> {

    @Override
    public QuestionClient create(Throwable cause) {
        log.error("QuestionClient 调用失败，触发熔断降级", cause);
        return new QuestionClient() {
            @Override
            public UserPracticeSummaryVO getPracticeSummary(Long userId, Integer recentInterviewLimit, Integer practicedLimit) {
                throw new BadRequestException(ErrorCode.SERVICE_UNAVAILABLE);
            }
        };
    }
}
