package com.prepdeck.quota.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.prepdeck.common.util.SecurityContext;
import com.prepdeck.quota.dto.response.QuotaCheckResult;
import com.prepdeck.quota.dto.response.QuotaVO;
import com.prepdeck.quota.entity.UserQuotaEntity;
import com.prepdeck.quota.mapper.UserQuotaMapper;
import com.prepdeck.quota.service.impl.QuotaServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.*;

@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
// 集成-1: QuotaService consume 原子操作 — 模拟真实用户配额消耗全流程
class QuotaServiceFlowTest {

    @Mock private UserQuotaMapper userQuotaMapper;
    @Mock private StringRedisTemplate stringRedisTemplate;

    private QuotaServiceImpl quotaService;
    private final List<UserQuotaEntity> db = new ArrayList<>();
    private MockedStatic<SecurityContext> securityMock;

    @BeforeEach
    void setUp() throws Exception {
        quotaService = new QuotaServiceImpl(userQuotaMapper, stringRedisTemplate);
        Field baseMapperField = ServiceImpl.class.getDeclaredField("baseMapper");
        baseMapperField.setAccessible(true);
        baseMapperField.set(quotaService, userQuotaMapper);
        db.clear();

        securityMock = mockStatic(SecurityContext.class);

        given(userQuotaMapper.selectById(anyLong())).willAnswer(inv -> {
            Long id = inv.getArgument(0);
            return db.stream().filter(e -> e.getUserId().equals(id)).findFirst().orElse(null);
        });

        given(userQuotaMapper.insert(any(UserQuotaEntity.class))).willAnswer(inv -> {
            db.add(inv.getArgument(0));
            return 1;
        });

        given(userQuotaMapper.insertIgnore(anyLong())).willReturn(1);

        given(userQuotaMapper.updateById(any(UserQuotaEntity.class))).willAnswer(inv -> {
            UserQuotaEntity e = inv.getArgument(0);
            db.removeIf(x -> x.getUserId().equals(e.getUserId()));
            db.add(e);
            return 1;
        });

        given(userQuotaMapper.incrementInterviewPaid(anyLong())).willAnswer(inv -> {
            Long userId = inv.getArgument(0);
            UserQuotaEntity e = db.stream().filter(x -> x.getUserId().equals(userId)).findFirst().orElse(null);
            if (e != null) e.setInterviewPaid(e.getInterviewPaid() + 1);
            return 1;
        });

        given(userQuotaMapper.incrementResumeChatPaid(anyLong())).willAnswer(inv -> {
            Long userId = inv.getArgument(0);
            UserQuotaEntity e = db.stream().filter(x -> x.getUserId().equals(userId)).findFirst().orElse(null);
            if (e != null) e.setResumeChatPaid(e.getResumeChatPaid() + 1);
            return 1;
        });

        given(userQuotaMapper.tryConsumeInterviewFree(anyLong(), anyInt())).willAnswer(inv -> {
            Long userId = inv.getArgument(0);
            UserQuotaEntity e = db.stream().filter(x -> x.getUserId().equals(userId)).findFirst().orElse(null);
            if (e != null && e.getInterviewFreeUsed() < 3) {
                e.setInterviewFreeUsed(e.getInterviewFreeUsed() + 1);
                return 1;
            }
            return 0;
        });

        given(userQuotaMapper.tryConsumeResumeChatFree(anyLong(), anyInt())).willAnswer(inv -> {
            Long userId = inv.getArgument(0);
            UserQuotaEntity e = db.stream().filter(x -> x.getUserId().equals(userId)).findFirst().orElse(null);
            if (e != null && e.getResumeChatFreeUsed() < 3) {
                e.setResumeChatFreeUsed(e.getResumeChatFreeUsed() + 1);
                return 1;
            }
            return 0;
        });

        given(userQuotaMapper.tryConsumeInterviewPaid(anyLong())).willAnswer(inv -> {
            Long userId = inv.getArgument(0);
            UserQuotaEntity e = db.stream().filter(x -> x.getUserId().equals(userId)).findFirst().orElse(null);
            if (e != null && e.getInterviewPaid() > 0) {
                e.setInterviewPaid(e.getInterviewPaid() - 1);
                return 1;
            }
            return 0;
        });

        given(userQuotaMapper.tryConsumeResumeChatPaid(anyLong())).willAnswer(inv -> {
            Long userId = inv.getArgument(0);
            UserQuotaEntity e = db.stream().filter(x -> x.getUserId().equals(userId)).findFirst().orElse(null);
            if (e != null && e.getResumeChatPaid() > 0) {
                e.setResumeChatPaid(e.getResumeChatPaid() - 1);
                return 1;
            }
            return 0;
        });

        given(userQuotaMapper.resetInterviewFreeUsed()).willAnswer(inv -> {
            db.forEach(e -> e.setInterviewFreeUsed(0));
            return 1;
        });
        given(userQuotaMapper.resetResumeChatFreeUsed()).willAnswer(inv -> {
            db.forEach(e -> e.setResumeChatFreeUsed(0));
            return 1;
        });
    }

    @AfterEach
    void tearDown() {
        if (securityMock != null) {
            securityMock.close();
        }
    }

    // ========== 场景1：新用户从注册到用完3次免费额度 ==========

    @Test
    void flow_newUser_firstConsume_createsQuotaAndAllowsFree() {
        securityMock.when(SecurityContext::getCurrentUserId).thenReturn(3001L);

        QuotaCheckResult r = quotaService.consume("INTERVIEW");
        assertTrue(r.allowed());
        assertEquals("FREE", r.type());
    }

    @Test
    void flow_newUser_threeFreeConsumes_autoCreate() {
        securityMock.when(SecurityContext::getCurrentUserId).thenReturn(3001L);

        quotaService.consume("INTERVIEW");
        quotaService.consume("INTERVIEW");
        quotaService.consume("INTERVIEW");

        UserQuotaEntity e = getQuota(3001L);
        assertNotNull(e);
        assertEquals(3, e.getInterviewFreeUsed());
    }

    @Test
    void flow_freeQuotaExhausted_withPaidQuota_allowsPaid() {
        securityMock.when(SecurityContext::getCurrentUserId).thenReturn(3002L);

        quotaService.consume("INTERVIEW");
        quotaService.consume("INTERVIEW");
        quotaService.consume("INTERVIEW");

        quotaService.grantPaid("INTERVIEW");

        QuotaCheckResult r = quotaService.consume("INTERVIEW");
        assertTrue(r.allowed());
        assertEquals("PAID", r.type());
        assertEquals(0, getQuota(3002L).getInterviewPaid());
    }

    @Test
    void flow_freeAndPaidExhausted_returnsDeniedWithCorrectPrice() {
        securityMock.when(SecurityContext::getCurrentUserId).thenReturn(3003L);

        quotaService.consume("INTERVIEW");
        quotaService.consume("INTERVIEW");
        quotaService.consume("INTERVIEW");

        QuotaCheckResult r = quotaService.consume("INTERVIEW");
        assertFalse(r.allowed());
        assertEquals("QUOTA_EXHAUSTED", r.reason());
        assertEquals(new BigDecimal("1.99"), r.price());
    }

    // ========== 场景2：付费消耗流程 ==========

    @Test
    void flow_paidQuota_decrementAfterUsage() {
        securityMock.when(SecurityContext::getCurrentUserId).thenReturn(3004L);

        quotaService.consume("INTERVIEW");
        quotaService.consume("INTERVIEW");
        quotaService.consume("INTERVIEW");
        quotaService.grantPaid("INTERVIEW");
        quotaService.grantPaid("INTERVIEW");

        assertEquals(2, getQuota(3004L).getInterviewPaid());

        quotaService.consume("INTERVIEW");
        assertEquals(1, getQuota(3004L).getInterviewPaid());
    }

    @Test
    void flow_paidQuotaDrained_deniedAfterDepletion() {
        securityMock.when(SecurityContext::getCurrentUserId).thenReturn(3005L);

        quotaService.consume("INTERVIEW");
        quotaService.consume("INTERVIEW");
        quotaService.consume("INTERVIEW");
        quotaService.grantPaid("INTERVIEW");
        quotaService.consume("INTERVIEW");

        QuotaCheckResult r = quotaService.consume("INTERVIEW");
        assertFalse(r.allowed());
    }

    // ========== 场景3：RESUME_CHAT 与 INTERVIEW 完全隔离 ==========

    @Test
    void flow_resumeChatQuotaIsolatedFromInterview() {
        securityMock.when(SecurityContext::getCurrentUserId).thenReturn(3007L);

        quotaService.consume("INTERVIEW");
        quotaService.consume("RESUME_CHAT");
        quotaService.consume("INTERVIEW");
        quotaService.consume("INTERVIEW");
        quotaService.consume("INTERVIEW");

        QuotaCheckResult interviewResult = quotaService.consume("INTERVIEW");
        assertFalse(interviewResult.allowed());

        QuotaCheckResult chatResult = quotaService.consume("RESUME_CHAT");
        assertTrue(chatResult.allowed());
        assertEquals("FREE", chatResult.type());
    }

    // ========== 场景4：原子性验证 ==========

    @Test
    void flow_consumeAtomic_freeAndPaidNotOverConsumed() {
        securityMock.when(SecurityContext::getCurrentUserId).thenReturn(3008L);

        UserQuotaEntity e = new UserQuotaEntity();
        e.setUserId(3008L);
        e.setInterviewFreeUsed(3);
        e.setResumeChatFreeUsed(0);
        e.setInterviewPaid(1);
        e.setResumeChatPaid(0);
        db.add(e);

        QuotaCheckResult r1 = quotaService.consume("INTERVIEW");
        assertTrue(r1.allowed());
        assertEquals("PAID", r1.type());
        assertEquals(0, getQuota(3008L).getInterviewPaid());

        QuotaCheckResult r2 = quotaService.consume("INTERVIEW");
        assertFalse(r2.allowed());
    }

    // ========== getQuota 流程 ==========

    @Test
    void flow_getQuota_newUser_autoCreatesAndReturnsDefaults() {
        securityMock.when(SecurityContext::getCurrentUserId).thenReturn(4001L);

        QuotaVO vo = quotaService.getQuota();
        assertNotNull(vo);
        assertEquals(4001L, vo.userId());
        assertEquals(0, vo.interviewFreeUsed());
    }

    @Test
    void flow_getQuota_afterUsage_returnsCurrentState() {
        securityMock.when(SecurityContext::getCurrentUserId).thenReturn(4002L);

        quotaService.consume("INTERVIEW");
        quotaService.consume("INTERVIEW");
        quotaService.grantPaid("INTERVIEW");

        QuotaVO vo = quotaService.getQuota();
        assertEquals(2, vo.interviewFreeUsed());
        assertEquals(1, vo.interviewPaid());
    }

    // ========== RESUME_CHAT 独立流程 ==========

    @Test
    void flow_resumeChat_freeAllowed_checksAndDeductsFree() {
        securityMock.when(SecurityContext::getCurrentUserId).thenReturn(4004L);

        quotaService.consume("RESUME_CHAT");

        UserQuotaEntity e = getQuota(4004L);
        assertEquals(1, e.getResumeChatFreeUsed());
        assertEquals(0, e.getInterviewFreeUsed());
    }

    @Test
    void flow_resumeChat_freeExhausted_withPaid_returnsPaid() {
        securityMock.when(SecurityContext::getCurrentUserId).thenReturn(4005L);

        quotaService.consume("RESUME_CHAT");
        quotaService.consume("RESUME_CHAT");
        quotaService.consume("RESUME_CHAT");
        quotaService.grantPaid("RESUME_CHAT");

        QuotaCheckResult r = quotaService.consume("RESUME_CHAT");
        assertTrue(r.allowed());
        assertEquals("PAID", r.type());
    }

    @Test
    void flow_resumeChat_paidAllowed_deductsPaidCorrectly() {
        securityMock.when(SecurityContext::getCurrentUserId).thenReturn(4006L);

        quotaService.consume("RESUME_CHAT");
        quotaService.consume("RESUME_CHAT");
        quotaService.consume("RESUME_CHAT");
        quotaService.grantPaid("RESUME_CHAT");
        quotaService.grantPaid("RESUME_CHAT");

        assertEquals(2, getQuota(4006L).getResumeChatPaid());

        quotaService.consume("RESUME_CHAT");
        assertEquals(1, getQuota(4006L).getResumeChatPaid());
    }

    @Test
    void flow_quotaTypesAreIndependent() {
        securityMock.when(SecurityContext::getCurrentUserId).thenReturn(4007L);

        quotaService.consume("INTERVIEW");
        quotaService.consume("RESUME_CHAT");
        quotaService.consume("INTERVIEW");
        quotaService.consume("INTERVIEW");
        quotaService.consume("INTERVIEW");

        QuotaCheckResult interviewResult = quotaService.consume("INTERVIEW");
        assertFalse(interviewResult.allowed());

        QuotaCheckResult chatResult = quotaService.consume("RESUME_CHAT");
        assertTrue(chatResult.allowed());
        assertEquals("FREE", chatResult.type());
    }

    // ========== 辅助方法 ==========

    private UserQuotaEntity getQuota(Long userId) {
        return db.stream().filter(x -> x.getUserId().equals(userId)).findFirst().orElse(null);
    }
}
