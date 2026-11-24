package com.prepdeck.question.service;

import com.prepdeck.common.dto.UserPracticeSummaryVO;
import com.prepdeck.question.dto.RecommendationRequest;
import com.prepdeck.question.dto.RecommendationVO;

public interface RecommendationService {

    RecommendationVO getRecommendations(RecommendationRequest request, UserPracticeSummaryVO summary);
}
