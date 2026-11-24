package com.prepdeck.common.ai;

import com.prepdeck.common.enums.ErrorCode;
import com.prepdeck.common.exception.BadRequestException;
import lombok.extern.slf4j.Slf4j;

/**
 * AiClient.chat() 降级处理。
 * Sentinel 要求 fallbackClass 中的方法为 static，且参数签名与原始方法一致（末尾可加 Throwable）。
 */
@Slf4j
public class AiClientFallback {

    public static String chatFallback(String systemPrompt, String userMessage, Throwable ex) {
        log.error("AI chat 调用失败，触发熔断降级: systemPrompt={}, userMessage={}", systemPrompt, userMessage, ex);
        throw new BadRequestException(ErrorCode.LLM_SERVICE_ERROR);
    }

    public static String chatFallback(String systemPrompt, String userMessage, String responseFormat, Throwable ex) {
        log.error("AI chat(json) 调用失败，触发熔断降级: systemPrompt={}, format={}", systemPrompt, responseFormat, ex);
        throw new BadRequestException(ErrorCode.LLM_SERVICE_ERROR);
    }
}
