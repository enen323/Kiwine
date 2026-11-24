package com.prepdeck.resume.dto.response;

import com.prepdeck.resume.dto.request.ResumeCreateRequest;

import java.util.List;

public record AiChatResponse(
        String type,
        String content,
        List<String> options,
        ResumeCreateRequest result
) {
    public static AiChatResponse text(String content) {
        return new AiChatResponse("text", content, List.of(), null);
    }

    public static AiChatResponse options(String content, List<String> options) {
        return new AiChatResponse("options", content, options, null);
    }

    public static AiChatResponse complete(ResumeCreateRequest result) {
        return new AiChatResponse("complete", null, List.of(), result);
    }

    public static AiChatResponse error(String content) {
        return new AiChatResponse("error", content, List.of(), null);
    }
}
