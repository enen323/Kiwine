package com.prepdeck.interview.client;

import com.prepdeck.common.dto.UserPracticeSummaryVO;
import org.springframework.stereotype.Component;

/**
 * 原 question-service Feign 客户端，单体化后改为本地直调。
 *
 * <p>契约说明：question-service 自项目初始提交起就没有
 * /api/v1/questions/recommendations/practice-summary 端点（git log --all -S
 * "recommendations/practice-summary" 仅命中本客户端文件自身），且本客户端在 interview-service 内
 * 无任何调用方（git log --all -S "questionClient" 零命中）。单体里没有对应的本地方法可直调，
 * 因此显式抛 UnsupportedOperationException，而不是伪造一个不存在的目标调用。</p>
 */
@Component
public class QuestionClient {

    public UserPracticeSummaryVO getPracticeSummary(Long userId,
                                                    Integer recentInterviewLimit,
                                                    Integer practicedLimit) {
        throw new UnsupportedOperationException(
                "question-service 无 /api/v1/questions/recommendations/practice-summary 端点，无本地方法可直调");
    }
}
