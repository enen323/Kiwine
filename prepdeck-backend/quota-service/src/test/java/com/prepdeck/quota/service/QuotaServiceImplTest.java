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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class QuotaServiceImplTest {

    @Mock
    private UserQuotaMapper userQuotaMapper;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    private QuotaServiceImpl quotaService;
    private MockedStatic<SecurityContext> securityMock;

    @BeforeEach
    void setUp() throws Exception {
        quotaService = new QuotaServiceImpl(userQuotaMapper, stringRedisTemplate);
        Field baseMapperField = ServiceImpl.class.getDeclaredField("baseMapper");
        baseMapperField.setAccessible(true);
        baseMapperField.set(quotaService, userQuotaMapper);

        securityMock = mockStatic(SecurityContext.class);
        securityMock.when(SecurityContext::getCurrentUserId).thenReturn(1001L);
    }

    @AfterEach
    void tearDown() {
        if (securityMock != null) {
            securityMock.close();
        }
    }

    // ========== consume 原子操作 ==========

    @Test
    void consume_freeAllowed_whenUnderLimit() {
        given(userQuotaMapper.tryConsumeInterviewFree(1001L, 3)).willReturn(1);

        QuotaCheckResult r = quotaService.consume("INTERVIEW");
        assertTrue(r.allowed());
        assertEquals("FREE", r.type());
        verify(userQuotaMapper).tryConsumeInterviewFree(1001L, 3);
        verify(userQuotaMapper, never()).tryConsumeInterviewPaid(anyLong());
    }

    @Test
    void consume_paidAllowed_whenFreeExhaustedButHasPaid() {
        given(userQuotaMapper.tryConsumeInterviewFree(1001L, 3)).willReturn(0);
        given(userQuotaMapper.tryConsumeInterviewPaid(1001L)).willReturn(1);

        QuotaCheckResult r = quotaService.consume("INTERVIEW");
        assertTrue(r.allowed());
        assertEquals("PAID", r.type());
        verify(userQuotaMapper).tryConsumeInterviewFree(1001L, 3);
        verify(userQuotaMapper).tryConsumeInterviewPaid(1001L);
    }

    @Test
    void consume_denied_whenFreeExhaustedAndNoPaid() {
        given(userQuotaMapper.tryConsumeInterviewFree(1001L, 3)).willReturn(0);
        given(userQuotaMapper.tryConsumeInterviewPaid(1001L)).willReturn(0);

        QuotaCheckResult r = quotaService.consume("INTERVIEW");
        assertFalse(r.allowed());
        assertEquals("QUOTA_EXHAUSTED", r.reason());
        assertEquals(new BigDecimal("1.99"), r.price());
    }

    @Test
    void consume_resumeChat_freeAllowed() {
        given(userQuotaMapper.tryConsumeResumeChatFree(1001L, 3)).willReturn(1);

        QuotaCheckResult r = quotaService.consume("RESUME_CHAT");
        assertTrue(r.allowed());
        assertEquals("FREE", r.type());
        verify(userQuotaMapper).tryConsumeResumeChatFree(1001L, 3);
    }

    @Test
    void consume_resumeChat_paidAllowed() {
        given(userQuotaMapper.tryConsumeResumeChatFree(1001L, 3)).willReturn(0);
        given(userQuotaMapper.tryConsumeResumeChatPaid(1001L)).willReturn(1);

        QuotaCheckResult r = quotaService.consume("RESUME_CHAT");
        assertTrue(r.allowed());
        assertEquals("PAID", r.type());
        verify(userQuotaMapper).tryConsumeResumeChatPaid(1001L);
    }

    // ========== deductFree / deductPaid / grantPaid ==========

    @Test
    void deductFreeIncrementsUsed() {
        given(userQuotaMapper.incrementInterviewFreeUsed(1001L)).willReturn(1);

        quotaService.deductFree("INTERVIEW");
        verify(userQuotaMapper).incrementInterviewFreeUsed(1001L);
    }

    @Test
    void deductPaidDecrementsPaid() {
        given(userQuotaMapper.decrementInterviewPaid(1001L)).willReturn(1);

        quotaService.deductPaid("INTERVIEW");
        verify(userQuotaMapper).decrementInterviewPaid(1001L);
    }

    @Test
    void grantPaidIncrementsPaid() {
        given(userQuotaMapper.incrementInterviewPaid(1001L)).willReturn(1);

        quotaService.grantPaid("INTERVIEW");
        verify(userQuotaMapper).incrementInterviewPaid(1001L);
        verify(userQuotaMapper, never()).selectById(anyLong());
        verify(userQuotaMapper, never()).updateById(any(com.prepdeck.quota.entity.UserQuotaEntity.class));
    }

    @Test
    void grantPaidWithUserId_incrementsPaidWithoutSecurityContext() {
        given(userQuotaMapper.incrementInterviewPaid(9999L)).willReturn(1);

        quotaService.grantPaid(9999L, "INTERVIEW");
        verify(userQuotaMapper).incrementInterviewPaid(9999L);
    }

    @Test
    void deductPaidClampsToZero() {
        given(userQuotaMapper.decrementInterviewPaid(1001L)).willReturn(1);

        quotaService.deductPaid("INTERVIEW");
        verify(userQuotaMapper).decrementInterviewPaid(1001L);
    }

    // ========== getQuota ==========

    @Test
    void getQuotaReturnsCurrentValues() {
        UserQuotaEntity entity = new UserQuotaEntity();
        entity.setUserId(1001L);
        entity.setInterviewFreeUsed(1);
        entity.setResumeChatFreeUsed(2);
        entity.setInterviewPaid(3);
        entity.setResumeChatPaid(4);
        given(userQuotaMapper.selectById(1001L)).willReturn(entity);

        QuotaVO vo = quotaService.getQuota();
        assertEquals(1, vo.interviewFreeUsed());
        assertEquals(2, vo.resumeChatFreeUsed());
        assertEquals(3, vo.interviewPaid());
        assertEquals(4, vo.resumeChatPaid());
    }

    // ========== resetMonthly 分布式锁 ==========

    @Test
    void resetMonthly_executesWhenLockAcquired() {
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        org.mockito.Mockito.when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        org.mockito.Mockito.when(valueOps.setIfAbsent(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(TimeUnit.class))).thenReturn(true);
        org.mockito.Mockito.when(stringRedisTemplate.delete(org.mockito.ArgumentMatchers.anyString())).thenReturn(true);
        given(userQuotaMapper.resetInterviewFreeUsed()).willReturn(1);
        given(userQuotaMapper.resetResumeChatFreeUsed()).willReturn(1);

        quotaService.resetMonthly();

        verify(userQuotaMapper).resetInterviewFreeUsed();
        verify(userQuotaMapper).resetResumeChatFreeUsed();
        verify(stringRedisTemplate).delete(anyString());
    }

    @Test
    void resetMonthly_skipsWhenLockNotAcquired() {
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        org.mockito.Mockito.when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        org.mockito.Mockito.when(valueOps.setIfAbsent(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(TimeUnit.class))).thenReturn(false);

        quotaService.resetMonthly();

        verify(userQuotaMapper, never()).update(any(), any());
    }
}
