package com.prepdeck.common.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@ConditionalOnProperty("spring.ai.deepseek.api-key")
public class DeepSeekClient implements AiClient {

    private final ChatClient chatClient;
    private static final ThreadLocal<Integer> tokenCounter = new ThreadLocal<>();

    public DeepSeekClient(
            ChatModel chatModel,
            @Value("${spring.ai.deepseek.api-key}") String apiKey) {
        this.chatClient = ChatClient.create(chatModel);
    }

    @Override
    @SentinelResource(value = "aiChat", fallback = "chatFallback", fallbackClass = AiClientFallback.class)
    public String chat(String systemPrompt, String userMessage) {
        return doChat(systemPrompt, userMessage, null);
    }

    @Override
    @SentinelResource(value = "aiChatJson", fallback = "chatFallback", fallbackClass = AiClientFallback.class)
    public String chat(String systemPrompt, String userMessage, String responseFormat) {
        return doChat(systemPrompt, userMessage, responseFormat);
    }

    private String doChat(String systemPrompt, String userMessage, String responseFormat) {
        String effectiveUserMessage = "json_object".equals(responseFormat)
                ? "请以JSON格式输出，不要包含任何其他内容。\n\n" + userMessage
                : userMessage;
        return chatClient.prompt()
                .system(systemPrompt)
                .user(effectiveUserMessage)
                .options(ChatOptions.builder()
                        .model("deepseek-v4-flash")
                        .temperature(0.7)
                        .build())
                .call()
                .content();
    }

    @Override
    public CompletableFuture<String> chatStream(String systemPrompt, List<Map<String, String>> messages, SseEmitter emitter) {
        String userMessage = extractLastUserMessage(messages);

        StringBuilder text = new StringBuilder();
        CompletableFuture<String> future = new CompletableFuture<>();

        try {
            chatClient.prompt()
                    .system(systemPrompt)
                    .user(userMessage)
                    .options(ChatOptions.builder()
                            .model("deepseek-v4-flash")
                            .temperature(0.7)
                            .build())
                    .stream()
                    .content()
                    .subscribe(
                            chunk -> {
                                text.append(chunk);
                                try {
                                    emitter.send(SseEmitter.event().name("chunk").data(chunk));
                                } catch (IOException e) {
                                    future.completeExceptionally(e);
                                }
                            },
                            error -> {
                                log.error("AI 流式调用异常，推送降级事件到 SSE", error);
                                try {
                                    emitter.send(SseEmitter.event()
                                            .name("error")
                                            .data("AI 服务暂时不可用，请稍后重试"));
                                    emitter.complete();
                                } catch (IOException ignored) {
                                    try { emitter.completeWithError(error); } catch (IllegalStateException ignored2) { }
                                }
                                future.completeExceptionally(error);
                            },
                            () -> future.complete(text.toString())
                    );
        } catch (Exception e) {
            future.completeExceptionally(e);
        }

        return future.thenApply(result -> {
            tokenCounter.set((int)(result.length() * 1.5));
            return result;
        }).whenComplete((result, ex) -> {
            tokenCounter.remove();
        });
    }

    private String extractLastUserMessage(List<Map<String, String>> messages) {
        return messages.stream()
                .filter(m -> "user".equals(m.get("role")))
                .reduce((first, second) -> second)
                .map(m -> m.get("content"))
                .orElse("");
    }

    public int getLastTokenCount() {
        Integer count = tokenCounter.get();
        return count != null ? count : 0;
    }

    public void clearTokenCounter() {
        tokenCounter.remove();
    }
}
