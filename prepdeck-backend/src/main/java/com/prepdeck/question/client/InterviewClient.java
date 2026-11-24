package com.prepdeck.question.client;

import com.prepdeck.common.dto.UserPracticeSummaryVO;
import com.prepdeck.interview.service.InterviewService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * 原 interview-service Feign 客户端，单体化后改为本地直调。
 *
 * <p>契约对照：InterviewController#getUserPracticeSummary（GET /api/v1/interviews/user-summary）→
 * interviewService.getUserPracticeSummary(userId, recentInterviewLimit, practicedLimit)。controller 从
 * SecurityContext 取 userId 并返回 Result&lt;UserPracticeSummaryVO&gt;，本客户端方法 historically 直接传 userId、
 * 返回 UserPracticeSummaryVO（原接口即如此），故此处解包返回 data 部分。</p>
 */
@Component
public class InterviewClient {

    @Resource
    private InterviewService interviewService;

    public UserPracticeSummaryVO getUserPracticeSummary(Long userId,
                                                        Integer recentInterviewLimit,
                                                        Integer practicedLimit) {
        return interviewService.getUserPracticeSummary(userId,
                recentInterviewLimit == null ? 5 : recentInterviewLimit,
                practicedLimit == null ? 50 : practicedLimit);
    }
}
