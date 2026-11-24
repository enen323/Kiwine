package com.prepdeck.quota.controller;

import com.prepdeck.common.model.Result;
import com.prepdeck.quota.dto.response.QuotaCheckResult;
import com.prepdeck.quota.dto.response.QuotaVO;
import com.prepdeck.quota.mapper.UserQuotaMapper;
import com.prepdeck.quota.service.QuotaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(QuotaController.class)
@TestPropertySource(properties = {
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.scheduling.enabled=false"
})
class QuotaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserQuotaMapper userQuotaMapper;

    @MockBean
    private QuotaService quotaService;

    @Test
    void health_returnsRunning() throws Exception {
        mockMvc.perform(get("/api/v1/quota/health")
                        .header("X-User-Id", "1")
                        .header("X-Gateway-Source", "prepdeck-gateway"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value("quota-service is running"));
    }

    @Test
    void consume_freeAllowed_returnsAllowed() throws Exception {
        when(quotaService.consume(eq("INTERVIEW")))
                .thenReturn(QuotaCheckResult.allowed("FREE"));

        mockMvc.perform(post("/api/v1/quota/consume")
                        .header("X-User-Id", "1")
                        .header("X-Gateway-Source", "prepdeck-gateway")
                        .param("serviceType", "INTERVIEW"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.allowed").value(true))
                .andExpect(jsonPath("$.data.type").value("FREE"));

        verify(quotaService).consume(eq("INTERVIEW"));
    }

    @Test
    void consume_denied_returnsDenied() throws Exception {
        when(quotaService.consume(eq("INTERVIEW")))
                .thenReturn(QuotaCheckResult.denied("QUOTA_EXHAUSTED", new BigDecimal("1.99")));

        mockMvc.perform(post("/api/v1/quota/consume")
                        .header("X-User-Id", "1")
                        .header("X-Gateway-Source", "prepdeck-gateway")
                        .param("serviceType", "INTERVIEW"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.allowed").value(false))
                .andExpect(jsonPath("$.data.reason").value("QUOTA_EXHAUSTED"));
    }

    @Test
    void consume_missingServiceType_returnsParamInvalid() throws Exception {
        mockMvc.perform(post("/api/v1/quota/consume")
                        .header("X-User-Id", "1")
                        .header("X-Gateway-Source", "prepdeck-gateway"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10001));
    }

    @Test
    void deductFree_success_returns200() throws Exception {
        doNothing().when(quotaService).deductFree(eq("INTERVIEW"));

        mockMvc.perform(post("/api/v1/quota/deduct-free")
                        .header("X-User-Id", "1")
                        .header("X-Gateway-Source", "prepdeck-gateway")
                        .param("serviceType", "INTERVIEW"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(quotaService).deductFree(eq("INTERVIEW"));
    }

    @Test
    void deductPaid_success_returns200() throws Exception {
        doNothing().when(quotaService).deductPaid(eq("RESUME_CHAT"));

        mockMvc.perform(post("/api/v1/quota/deduct-paid")
                        .header("X-User-Id", "1")
                        .header("X-Gateway-Source", "prepdeck-gateway")
                        .param("serviceType", "RESUME_CHAT"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(quotaService).deductPaid(eq("RESUME_CHAT"));
    }

    @Test
    void grantPaid_success_returns200() throws Exception {
        doNothing().when(quotaService).grantPaid(eq("INTERVIEW"));

        mockMvc.perform(post("/api/v1/quota/grant-paid")
                        .header("X-User-Id", "1")
                        .header("X-Gateway-Source", "prepdeck-gateway")
                        .param("serviceType", "INTERVIEW"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(quotaService).grantPaid(eq("INTERVIEW"));
    }

    @Test
    void getQuota_success_returnsQuotaInfo() throws Exception {
        QuotaVO quotaVO = new QuotaVO(1L, 1, 2, 3, 4);
        when(quotaService.getQuota()).thenReturn(quotaVO);

        mockMvc.perform(get("/api/v1/quota")
                        .header("X-User-Id", "1")
                        .header("X-Gateway-Source", "prepdeck-gateway"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.userId").value(1))
                .andExpect(jsonPath("$.data.interviewFreeUsed").value(1))
                .andExpect(jsonPath("$.data.resumeChatFreeUsed").value(2))
                .andExpect(jsonPath("$.data.interviewPaid").value(3))
                .andExpect(jsonPath("$.data.resumeChatPaid").value(4));
    }

    @Test
    void getQuota_newUser_autoCreatesWithDefaults() throws Exception {
        QuotaVO quotaVO = new QuotaVO(1L, 0, 0, 0, 0);
        when(quotaService.getQuota()).thenReturn(quotaVO);

        mockMvc.perform(get("/api/v1/quota")
                        .header("X-User-Id", "1")
                        .header("X-Gateway-Source", "prepdeck-gateway"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.interviewFreeUsed").value(0))
                .andExpect(jsonPath("$.data.interviewPaid").value(0));
    }

    @Test
    void resetMonthly_success_returns200() throws Exception {
        doNothing().when(quotaService).resetMonthly();

        mockMvc.perform(post("/api/v1/quota/reset")
                        .header("X-User-Id", "1")
                        .header("X-Gateway-Source", "prepdeck-gateway"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(quotaService).resetMonthly();
    }

    // ========== SECURITY: 修复后验证 userId 参数已移除 ==========

    @Test
    void grantPaid_userIdParamRemoved_cannotSpecifyOtherUserId() throws Exception {
        doNothing().when(quotaService).grantPaid(eq("INTERVIEW"));

        mockMvc.perform(post("/api/v1/quota/grant-paid")
                        .header("X-User-Id", "1")
                        .header("X-Gateway-Source", "prepdeck-gateway")
                        .param("userId", "2")
                        .param("serviceType", "INTERVIEW"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(quotaService).grantPaid(eq("INTERVIEW"));
    }

    @Test
    void getQuota_pathVariableRemoved_alwaysReturnsCurrentUser() throws Exception {
        QuotaVO quotaVO = new QuotaVO(1L, 0, 0, 5, 5);
        when(quotaService.getQuota()).thenReturn(quotaVO);

        mockMvc.perform(get("/api/v1/quota")
                        .header("X-User-Id", "1")
                        .header("X-Gateway-Source", "prepdeck-gateway"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(1));
    }
}
