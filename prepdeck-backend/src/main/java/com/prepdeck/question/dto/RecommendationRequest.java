package com.prepdeck.question.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record RecommendationRequest(
        @Min(1) @Max(50) Integer count
) {}
