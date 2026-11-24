package com.prepdeck.interview.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 提交回答请求
 */
public record SubmitAnswerRequest(

        @NotBlank(message = "回答内容不能为空")
        @Size(max = 5000, message = "回答内容不能超过5000字")
        String content
) {}
