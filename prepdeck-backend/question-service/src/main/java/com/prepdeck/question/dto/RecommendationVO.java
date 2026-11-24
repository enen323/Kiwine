package com.prepdeck.question.dto;

public record RecommendationVO(
        RecommendationStrategy strategy,
        String message,
        java.util.List<QuestionDetailVO> questions
) {
    public enum RecommendationStrategy {
        WEAK_AREA,
        TARGET_COMPANY,
        HOT
    }
}
