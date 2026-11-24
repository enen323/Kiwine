package com.prepdeck.question.client;

import com.prepdeck.common.dto.UserPracticeSummaryVO;
import com.prepdeck.common.enums.ErrorCode;
import com.prepdeck.common.exception.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class InterviewClientFallbackFactory implements FallbackFactory<InterviewClient> {

    @Override
    public InterviewClient create(Throwable cause) {
        log.error("InterviewClient 调用失败，触发熔断降级", cause);
        return new InterviewClient() {
            @Override
            public UserPracticeSummaryVO getUserPracticeSummary(Long userId, Integer recentInterviewLimit, Integer practicedLimit) {
                throw new BadRequestException(ErrorCode.SERVICE_UNAVAILABLE);
            }
        };
    }
}
