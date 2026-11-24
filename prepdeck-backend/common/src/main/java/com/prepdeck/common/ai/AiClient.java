package com.prepdeck.common.ai;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface AiClient {
    String chat(String systemPrompt, String userMessage);
    String chat(String systemPrompt, String userMessage, String responseFormat);
    CompletableFuture<String> chatStream(String systemPrompt, List<Map<String, String>> messages, SseEmitter emitter);
}
