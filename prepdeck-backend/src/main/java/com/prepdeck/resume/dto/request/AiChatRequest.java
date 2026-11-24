package com.prepdeck.resume.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiChatRequest(
        @NotBlank String sessionId,
        @NotBlank @Size(max = 2000) String message
) {
}
