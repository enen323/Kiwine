package com.prepdeck.question.client;

import com.prepdeck.common.dto.UserPracticeSummaryVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "interview-service", path = "/api/v1/interviews",
        fallbackFactory = InterviewClientFallbackFactory.class)
public interface InterviewClient {

    @GetMapping("/user-summary")
    UserPracticeSummaryVO getUserPracticeSummary(
            @RequestParam Long userId,
            @RequestParam(required = false, defaultValue = "5") Integer recentInterviewLimit,
            @RequestParam(required = false, defaultValue = "50") Integer practicedLimit
    );
}
