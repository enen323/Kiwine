package com.prepdeck.resume.service.impl;

import com.prepdeck.common.ai.AiClient;
import com.prepdeck.common.dto.QuotaCheckResult;
import com.prepdeck.common.enums.ErrorCode;
import com.prepdeck.common.exception.BusinessException;
import com.prepdeck.common.model.Result;
import com.prepdeck.resume.client.QuotaClient;
import com.prepdeck.resume.dto.request.AiChatRequest;
import com.prepdeck.resume.dto.response.AiChatResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
// P0-3: aiChat 配额检查与扣减 — 首轮扣减、续轮跳过、session隔离、异常传播
class ResumeAiChatQuotaTest {

    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private QuotaClient quotaClient;
    @Mock private AiClient aiClient;

    private ResumeServiceImpl resumeService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    @BeforeEach
    void setUp() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        resumeService = new ResumeServiceImpl(null, null, aiClient, objectMapper, stringRedisTemplate, quotaClient);
    }

    // ---- 首轮对话：配额检查与扣减 ----

    // 测：新 session 首轮对话 → check + deductFree（FREE类型）
    @Test
    void aiChat_newSession_freeAllowed_callsDeductFree() {
        stubNewSession(aiClient);

        AiChatRequest request = new AiChatRequest("session-1", "帮我写简历");
        AiChatResponse response = resumeService.aiChat(1L, request);

        assertNotNull(response);
        verify(quotaClient).consume(eq("RESUME_CHAT"));
    }

    // 测：新 session 首轮对话 → check 返回 PAID → 调用 deductPaid，不调用 deductFree
    @Test
    void aiChat_newSession_paidAllowed_callsDeductPaid() {
        when(valueOps.get("resume:ai:session:1:session-1")).thenReturn(null);
        when(valueOps.setIfAbsent("resume:ai:lock:1", "1", 30, java.util.concurrent.TimeUnit.SECONDS)).thenReturn(true);
        when(quotaClient.consume(eq("RESUME_CHAT")))
                .thenReturn(Result.success(QuotaCheckResult.allowed("PAID")));

        AiChatResponse response = resumeService.aiChat(1L, new AiChatRequest("session-1", "帮我写简历"));

        assertNotNull(response);
        verify(quotaClient).consume(eq("RESUME_CHAT"));
    }

    // 测：新 session 配额耗尽 → 返回 error 响应，consumeFree 被调用但结果 denied
    @Test
    void aiChat_newSession_quotaExhausted_returnsErrorResponse() {
        when(valueOps.get("resume:ai:session:1:session-1")).thenReturn(null);
        when(valueOps.setIfAbsent("resume:ai:lock:1", "1", 30, java.util.concurrent.TimeUnit.SECONDS)).thenReturn(true);
        when(quotaClient.consume(eq("RESUME_CHAT")))
                .thenReturn(Result.success(QuotaCheckResult.denied("QUOTA_EXHAUSTED", new java.math.BigDecimal("0.99"))));

        AiChatResponse response = resumeService.aiChat(1L, new AiChatRequest("session-1", "帮我写简历"));

        assertEquals("error", response.type());
        assertTrue(response.content().contains("稍后重试"));

        verify(quotaClient).consume(eq("RESUME_CHAT"));
    }

    // ---- 续轮对话：跳过配额检查 ----

    // 测：同一 session 第二次对话（historyJson != null）→ 不调用 quotaClient.consumeFree/deduct
    // 风险：如果此逻辑错误，每轮都扣配额，用户会话消耗双倍额度
    @Test
    void aiChat_sameSessionSecondRound_skipsQuotaCheck() throws Exception {
        List<Map<String, String>> existingHistory = new ArrayList<>();
        existingHistory.add(Map.of("role", "user", "content", "第一轮"));
        existingHistory.add(Map.of("role", "assistant", "content", "{\"type\":\"text\",\"content\":\"你好\"}"));

        when(valueOps.get("resume:ai:session:1:session-1"))
                .thenReturn(objectMapper.writeValueAsString(existingHistory));
        when(valueOps.get("resume:ai:rounds:1:session-1")).thenReturn("1");
        when(valueOps.setIfAbsent("resume:ai:lock:1", "1", 30, java.util.concurrent.TimeUnit.SECONDS)).thenReturn(true);

        // 模拟 AI 返回非 complete 的追问
        when(aiClient.chat(anyString(), anyString(), eq("json_object")))
                .thenReturn("{\"type\":\"text\",\"content\":\"请告诉我你的工作经历\"}");

        AiChatResponse response = resumeService.aiChat(1L, new AiChatRequest("session-1", "我有3年Java经验"));

        assertNotNull(response);
        assertEquals("text", response.type());
        verify(quotaClient, never()).consume(anyString());
    }

    // ---- session 隔离：不同 session 独立扣配额 ----

    // 测：两个不同 sessionId 分别首轮对话 → 各扣各的配额，互不影响
    @Test
    void aiChat_differentSessions_quotaDeductedIndependently() {
        // session-A: 首轮
        when(valueOps.get("resume:ai:session:1:session-A")).thenReturn(null);
        when(valueOps.setIfAbsent("resume:ai:lock:1", "1", 30, java.util.concurrent.TimeUnit.SECONDS)).thenReturn(true);
        when(quotaClient.consume(eq("RESUME_CHAT")))
                .thenReturn(Result.success(QuotaCheckResult.allowed("FREE")));

        resumeService.aiChat(1L, new AiChatRequest("session-A", "帮我写简历"));

        verify(quotaClient).consume(eq("RESUME_CHAT"));

        // session-B: 首轮，独立扣减
        when(valueOps.get("resume:ai:session:1:session-B")).thenReturn(null);
        when(valueOps.setIfAbsent("resume:ai:lock:1", "1", 30, java.util.concurrent.TimeUnit.SECONDS)).thenReturn(true);
        when(quotaClient.consume(eq("RESUME_CHAT")))
                .thenReturn(Result.success(QuotaCheckResult.allowed("FREE")));

        resumeService.aiChat(1L, new AiChatRequest("session-B", "帮我写简历"));

        verify(quotaClient, times(2)).consume(eq("RESUME_CHAT"));
    }

    // ---- 并发锁：同一 session 并发请求互斥 ----

    // 测：同一 session 并发请求，第二个请求获取锁失败 → 返回"处理中"错误，不扣配额
    @Test
    void aiChat_concurrentSameSession_secondRequestReturnsBusy() {
        when(valueOps.get("resume:ai:session:1:session-1")).thenReturn(null);
        // 第一次请求获取锁成功，第二次失败
        when(valueOps.setIfAbsent("resume:ai:lock:1", "1", 30, java.util.concurrent.TimeUnit.SECONDS))
                .thenReturn(true, false);

        AiChatResponse first = resumeService.aiChat(1L, new AiChatRequest("session-1", "帮我写简历"));
        assertNotNull(first);

        AiChatResponse second = resumeService.aiChat(1L, new AiChatRequest("session-1", "再来一次"));
        assertNotNull(second);
        assertEquals("error", second.type());
        assertTrue(second.content().contains("处理中"));

        // 仅第一次调用 consumeFree，第二次被锁拦截
        verify(quotaClient, times(1)).consume(eq("RESUME_CHAT"));
    }

    // ---- 不同用户 session 隔离 ----

    // 测：不同 userId 的相同 sessionId → 扣减各自配额，key 中包含 userId
    @Test
    void aiChat_differentUsers_sameSessionId_quotaIsolated() {
        // user-1 的 session
        when(valueOps.get("resume:ai:session:1:session-1")).thenReturn(null);
        when(valueOps.setIfAbsent("resume:ai:lock:1", "1", 30, java.util.concurrent.TimeUnit.SECONDS)).thenReturn(true);
        when(quotaClient.consume(eq("RESUME_CHAT")))
                .thenReturn(Result.success(QuotaCheckResult.allowed("FREE")));

        resumeService.aiChat(1L, new AiChatRequest("session-1", "帮我写简历"));

        // user-2 的 session（相同 sessionId 字符串，但 userId 不同 → Redis key 不同）
        when(valueOps.get("resume:ai:session:2:session-1")).thenReturn(null);
        when(valueOps.setIfAbsent("resume:ai:lock:2", "1", 30, java.util.concurrent.TimeUnit.SECONDS)).thenReturn(true);
        when(quotaClient.consume(eq("RESUME_CHAT")))
                .thenReturn(Result.success(QuotaCheckResult.allowed("PAID")));

        resumeService.aiChat(2L, new AiChatRequest("session-1", "帮我写简历"));

        verify(quotaClient, times(2)).consume(eq("RESUME_CHAT"));
        verify(quotaClient, times(2)).consume(eq("RESUME_CHAT"));
    }

    // ---- 异常传播 ----

    // 测：quotaClient.consumeFree 抛异常 → 返回 error 响应
    @Test
    void aiChat_quotaCheckThrows_returnsErrorResponse() {
        when(valueOps.get("resume:ai:session:1:session-1")).thenReturn(null);
        when(valueOps.setIfAbsent("resume:ai:lock:1", "1", 30, java.util.concurrent.TimeUnit.SECONDS)).thenReturn(true);
        when(quotaClient.consume(eq("RESUME_CHAT")))
                .thenThrow(new RuntimeException("Feign timeout"));

        AiChatResponse response = resumeService.aiChat(1L, new AiChatRequest("session-1", "帮我写简历"));

        assertEquals("error", response.type());
        assertTrue(response.content().contains("稍后重试"));

        verify(quotaClient).consume(eq("RESUME_CHAT"));
    }

    // ---- 临界：freeUsed=3 恰好用完，第4次对话必须付费 ----

    // 测：免费额度恰好用完（freeUsed=3）→ check 返回 PAID → 走 deductPaid
    @Test
    void aiChat_freeQuotaExactlyExhausted_fallsBackToPaid() {
        when(valueOps.get("resume:ai:session:1:session-1")).thenReturn(null);
        when(valueOps.setIfAbsent("resume:ai:lock:1", "1", 30, java.util.concurrent.TimeUnit.SECONDS)).thenReturn(true);
        when(quotaClient.consume(eq("RESUME_CHAT")))
                .thenReturn(Result.success(QuotaCheckResult.allowed("PAID")));

        resumeService.aiChat(1L, new AiChatRequest("session-1", "帮我写简历"));

        verify(quotaClient).consume(eq("RESUME_CHAT"));
    }

    // ========== 辅助 ==========

    // 通用 stub：新 session 首轮 FREE 配额允许
    private void stubNewSession(AiClient aiClient) {
        when(valueOps.get("resume:ai:session:1:session-1")).thenReturn(null);
        when(valueOps.setIfAbsent("resume:ai:lock:1", "1", 30, java.util.concurrent.TimeUnit.SECONDS)).thenReturn(true);
        when(quotaClient.consume(eq("RESUME_CHAT")))
                .thenReturn(Result.success(QuotaCheckResult.allowed("FREE")));
        when(aiClient.chat(anyString(), anyString(), eq("json_object")))
                .thenReturn("{\"type\":\"text\",\"content\":\"你好，请告诉我你的基本信息\"}");
    }
}
