package com.prepdeck.common.dto;

import java.util.List;

public record UserPracticeSummaryVO(
        List<UserInterviewScoreVO> recentScores,
        List<String> recentPracticedDescriptions
) {}
