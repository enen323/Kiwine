package com.prepdeck.interview.client;

import com.prepdeck.common.dto.UserPracticeSummaryVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "question-service", path = "/api/v1/questions",
        fallbackFactory = QuestionClientFallbackFactory.class)
public interface QuestionClient {

    @GetMapping("/recommendations/practice-summary")
    UserPracticeSummaryVO getPracticeSummary(
            @RequestParam Long userId,
            @RequestParam(required = false, defaultValue = "5") Integer recentInterviewLimit,
            @RequestParam(required = false, defaultValue = "50") Integer practicedLimit
    );
}
