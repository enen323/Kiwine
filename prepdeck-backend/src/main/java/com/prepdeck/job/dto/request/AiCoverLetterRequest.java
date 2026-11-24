package com.prepdeck.job.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AiCoverLetterRequest(
        String applicationId,
        @NotBlank(message = "请填写目标公司")
        String company,
        @NotBlank(message = "请填写目标职位")
        String position,
        String jobDescription,
        String extraNotes,
        @NotBlank(message = "请选择输出语言")
        String outputLanguage
) {
}
