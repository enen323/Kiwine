package com.prepdeck.interview.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.prepdeck.common.dto.QuotaCheckResult;
import com.prepdeck.common.enums.ErrorCode;
import com.prepdeck.common.exception.BadRequestException;
import com.prepdeck.common.exception.BusinessException;
import com.prepdeck.interview.client.QuotaClient;
import com.prepdeck.interview.client.ResumeClient;
import com.prepdeck.interview.domain.InterviewEntity;
import com.prepdeck.interview.domain.InterviewMessageEntity;
import com.prepdeck.interview.dto.request.StartInterviewRequest;
import com.prepdeck.interview.dto.response.StartInterviewResponse;
import com.prepdeck.interview.enums.CompanyEnum;
import com.prepdeck.interview.enums.InterviewStatusEnum;
import com.prepdeck.interview.enums.MessageRoleEnum;
import com.prepdeck.interview.enums.PositionEnum;
import com.prepdeck.interview.enums.QuestionTypeEnum;
import com.prepdeck.interview.repository.InterviewMapper;
import com.prepdeck.interview.repository.InterviewMessageMapper;
import com.prepdeck.interview.repository.InterviewQuestionScoreMapper;
import com.prepdeck.interview.service.engine.InterviewEngine;
import com.prepdeck.resume.dto.response.ResumeVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
// P0-1: startInterview 配额检查与扣减 — 配额黑洞防护、扣减分支、异常传播
class InterviewServiceImplQuotaTest {

    @Mock private InterviewMapper interviewMapper;
    @Mock private InterviewMessageMapper messageMapper;
    @Mock private InterviewQuestionScoreMapper questionScoreMapper;
    @Mock private InterviewEngine interviewEngine;
    @Mock private ResumeClient resumeClient;
    @Mock private java.util.concurrent.Executor taskExecutor;
    @Mock private java.util.concurrent.ScheduledExecutorService interviewScheduler;
    @Mock private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;
    @Mock private QuotaClient quotaClient;

    private InterviewServiceImpl interviewService;

    @BeforeEach
    void setUp() {
        InterviewServiceImpl.clearAsyncTaskQueuesForTest();
        var valueOps = org.mockito.Mockito.mock(org.springframework.data.redis.core.ValueOperations.class);
        org.mockito.Mockito.lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        org.mockito.Mockito.lenient().when(valueOps.setIfAbsent(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any())).thenReturn(true);
        org.mockito.Mockito.lenient().when(stringRedisTemplate.hasKey(org.mockito.ArgumentMatchers.anyString())).thenReturn(false);
        org.mockito.Mockito.lenient().when(stringRedisTemplate.delete(org.mockito.ArgumentMatchers.anyString())).thenReturn(true);
        
        interviewService = new InterviewServiceImpl(interviewMapper, messageMapper, questionScoreMapper,
                interviewEngine, resumeClient, taskExecutor, interviewScheduler, quotaClient, stringRedisTemplate);
    }

    // ---- 配额耗尽：零副作用 ----

    // 测：quotaClient.consume 返回 denied → 抛 BusinessException，不创建面试、不扣配额、不调引擎
    @Test
    void startInterview_quotaExhausted_doesNotCreateInterviewOrCallEngine() {
        when(quotaClient.consume(eq("INTERVIEW")))
                .thenReturn(com.prepdeck.common.model.Result.success(QuotaCheckResult.denied("QUOTA_EXHAUSTED", null)));

        StartInterviewRequest request = new StartInterviewRequest(CompanyEnum.ALIBABA, PositionEnum.BACKEND, null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> interviewService.startInterview(1L, request));
        assertEquals(ErrorCode.QUOTA_EXHAUSTED.getCode(), ex.getCode());

        verify(interviewMapper, never()).insert(any(InterviewEntity.class));
        verify(messageMapper, never()).insert(any(InterviewMessageEntity.class));
        verify(interviewEngine, never()).generateFirstQuestion(any(), any());
        verify(quotaClient, never()).deductFree(anyString());
        verify(quotaClient, never()).deductPaid(anyString());
    }

    // ---- 扣减分支：FREE vs PAID ----

    // 测：consume 返回 FREE → 调用 consume，绝不调用 deductPaid
    @Test
    void startInterview_freeAllowed_callsConsumeFreeOnly() {
        when(quotaClient.consume(eq("INTERVIEW")))
                .thenReturn(com.prepdeck.common.model.Result.success(QuotaCheckResult.allowed("FREE")));
        when(interviewMapper.selectCount(any())).thenReturn(0L);
        doAnswer(inv -> { InterviewEntity e = inv.getArgument(0); e.setId(100L); return 1; })
                .when(interviewMapper).insert(any(InterviewEntity.class));

        InterviewMessageEntity firstMsg = buildFirstMessage(100L, 200L);
        when(interviewEngine.generateFirstQuestion(any(), any())).thenReturn(firstMsg);
        when(messageMapper.insert(any(InterviewMessageEntity.class))).thenReturn(1);

        interviewService.startInterview(1L, new StartInterviewRequest(CompanyEnum.ALIBABA, PositionEnum.BACKEND, null));

        verify(quotaClient).consume(eq("INTERVIEW"));
        verify(quotaClient, never()).deductPaid(anyString());
    }

    // 测：consume 返回 PAID → 调用 consume 且 deductPaid，绝不调用 deductFree
    @Test
    void startInterview_paidAllowed_callsDeductPaidOnly() {
        when(quotaClient.consume(eq("INTERVIEW")))
                .thenReturn(com.prepdeck.common.model.Result.success(QuotaCheckResult.allowed("PAID")));
        when(interviewMapper.selectCount(any())).thenReturn(0L);
        doAnswer(inv -> { InterviewEntity e = inv.getArgument(0); e.setId(100L); return 1; })
                .when(interviewMapper).insert(any(InterviewEntity.class));

        InterviewMessageEntity firstMsg = buildFirstMessage(100L, 200L);
        when(interviewEngine.generateFirstQuestion(any(), any())).thenReturn(firstMsg);
        when(messageMapper.insert(any(InterviewMessageEntity.class))).thenReturn(1);

        interviewService.startInterview(1L, new StartInterviewRequest(CompanyEnum.ALIBABA, PositionEnum.BACKEND, null));

        verify(quotaClient).consume(eq("INTERVIEW"));
        verify(quotaClient, never()).deductPaid(anyString());
    }

    // ---- 活跃面试检查先于扣减 ----

    // 测：存在进行中面试 → 抛出 BadRequestException，且配额扣减接口（consume）绝不调用、不创建记录
    @Test
    void startInterview_activeInterviewExists_doesNotDeductQuota() {
        when(quotaClient.consume(eq("INTERVIEW")))
                .thenReturn(com.prepdeck.common.model.Result.success(QuotaCheckResult.allowed("FREE")));
        when(interviewMapper.selectCount(any())).thenReturn(1L);

        StartInterviewRequest request = new StartInterviewRequest(CompanyEnum.ALIBABA, PositionEnum.BACKEND, null);
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> interviewService.startInterview(1L, request));
        assertTrue(ex.getMessage().contains("进行中"));

        // 关键断言：活跃面试校验必须先于配额扣减，consume 绝不能触发（否则真实环境会扣配额）
        verify(quotaClient, never()).consume(anyString());
        verify(quotaClient, never()).deductFree(anyString());
        verify(quotaClient, never()).deductPaid(anyString());
        verify(interviewMapper, never()).insert(any(InterviewEntity.class));
    }

    // ---- 正常路径：初始状态正确 ----

    // 测：首次创建面试，实体状态为 CREATED，company/position/totalQuestions 正确写入
    @Test
    void startInterview_noActiveInterview_createsWithCorrectInitialState() {
        when(quotaClient.consume(eq("INTERVIEW")))
                .thenReturn(com.prepdeck.common.model.Result.success(QuotaCheckResult.allowed("FREE")));
        when(interviewMapper.selectCount(any())).thenReturn(0L);
        doAnswer(inv -> { InterviewEntity e = inv.getArgument(0); e.setId(100L); return 1; })
                .when(interviewMapper).insert(any(InterviewEntity.class));

        InterviewMessageEntity firstMsg = buildFirstMessage(100L, 200L);
        when(interviewEngine.generateFirstQuestion(any(), any())).thenReturn(firstMsg);
        when(messageMapper.insert(any(InterviewMessageEntity.class))).thenReturn(1);

        StartInterviewRequest request = new StartInterviewRequest(CompanyEnum.ALIBABA, PositionEnum.BACKEND, null);
        StartInterviewResponse response = interviewService.startInterview(1L, request);

        ArgumentCaptor<InterviewEntity> captor = ArgumentCaptor.forClass(InterviewEntity.class);
        verify(interviewMapper).insert(captor.capture());
        InterviewEntity saved = captor.getValue();
        assertEquals(InterviewStatusEnum.CREATED, saved.getStatus());
        assertEquals(1L, saved.getUserId());
        assertEquals("ALIBABA", saved.getCompany());
        assertEquals("BACKEND", saved.getPosition());
        assertEquals(0, saved.getTotalQuestions());
    }

    // ---- Feign 调用异常传播 ----

    // 测：quotaClient.consume 抛异常 → 异常直接向上传播，不创建任何记录
    @Test
    void startInterview_quotaCheckThrows_exceptionPropagates() {
        when(quotaClient.consume(eq("INTERVIEW")))
                .thenThrow(new RuntimeException("Feign connection refused"));

        assertThrows(RuntimeException.class,
                () -> interviewService.startInterview(1L, new StartInterviewRequest(CompanyEnum.ALIBABA, PositionEnum.BACKEND, null)));

        verify(interviewMapper, never()).insert(any(InterviewEntity.class));
        verify(quotaClient, never()).deductFree(anyString());
        verify(quotaClient, never()).deductPaid(anyString());
    }

    // ---- 多用户配额隔离 ----

    // 测：两个用户分别创建面试，配额扣减各自独立，userId 不会串号
    @Test
    void startInterview_differentUsers_quotaDeductedIndependently() {
        when(quotaClient.consume(eq("INTERVIEW")))
                .thenReturn(com.prepdeck.common.model.Result.success(QuotaCheckResult.allowed("FREE")));
        when(interviewMapper.selectCount(any())).thenReturn(0L);
        doAnswer(inv -> { InterviewEntity e = inv.getArgument(0); e.setId(100L); return 1; })
                .when(interviewMapper).insert(any(InterviewEntity.class));

        InterviewMessageEntity firstMsg = buildFirstMessage(100L, 200L);
        when(interviewEngine.generateFirstQuestion(any(), any())).thenReturn(firstMsg);
        when(messageMapper.insert(any(InterviewMessageEntity.class))).thenReturn(1);

        interviewService.startInterview(1L, new StartInterviewRequest(CompanyEnum.ALIBABA, PositionEnum.BACKEND, null));

        verify(quotaClient).consume(eq("INTERVIEW"));
    }

    // ---- serviceType 字符串精确匹配 ----

    // 测：consume 用 "INTERVIEW" 精确匹配，小写 "interview" 不进入任何分支
    @Test
    void startInterview_serviceTypeIsExactMatch_stringCaseSensitive() {
        when(quotaClient.consume(eq("INTERVIEW")))
                .thenReturn(com.prepdeck.common.model.Result.success(QuotaCheckResult.allowed("FREE")));
        when(interviewMapper.selectCount(any())).thenReturn(0L);
        doAnswer(inv -> { InterviewEntity e = inv.getArgument(0); e.setId(100L); return 1; })
                .when(interviewMapper).insert(any(InterviewEntity.class));

        InterviewMessageEntity firstMsg = buildFirstMessage(100L, 200L);
        when(interviewEngine.generateFirstQuestion(any(), any())).thenReturn(firstMsg);
        when(messageMapper.insert(any(InterviewMessageEntity.class))).thenReturn(1);

        interviewService.startInterview(1L, new StartInterviewRequest(CompanyEnum.ALIBABA, PositionEnum.BACKEND, null));

        verify(quotaClient).consume(eq("INTERVIEW"));
        verify(quotaClient, never()).consume(eq("interview"));
    }

    // ---- 有简历时正确加载 ----

    // 测：startInterview 传入 resumeId，正确调用 resumeClient.getById
    @Test
    void startInterview_withResumeId_loadsResumeProfile() {
        when(quotaClient.consume(eq("INTERVIEW")))
                .thenReturn(com.prepdeck.common.model.Result.success(QuotaCheckResult.allowed("FREE")));
        when(interviewMapper.selectCount(any())).thenReturn(0L);
        doAnswer(inv -> { InterviewEntity e = inv.getArgument(0); e.setId(1L); return 1; })
                .when(interviewMapper).insert(any(InterviewEntity.class));

        InterviewMessageEntity firstMsg = buildFirstMessage(1L, 200L);
        when(interviewEngine.generateFirstQuestion(any(), any())).thenReturn(firstMsg);
        when(messageMapper.insert(any(InterviewMessageEntity.class))).thenReturn(1);

        ResumeVO resumeVO = new ResumeVO(100L, 1L, "resume.pdf", null, null, null, true,
                "我的简历", null, null, null, true, null, 0, List.of(), LocalDateTime.now(), LocalDateTime.now());
        when(resumeClient.getById(100L)).thenReturn(resumeVO);

        StartInterviewRequest request = new StartInterviewRequest(CompanyEnum.ALIBABA, PositionEnum.BACKEND, 100L);
        StartInterviewResponse response = interviewService.startInterview(1L, request);

        verify(resumeClient).getById(100L);
        assertNotNull(response);
        assertNotNull(response.resumeProfile());
    }

    // ========== 辅助方法 ==========

    private InterviewMessageEntity buildFirstMessage(Long interviewId, Long messageId) {
        InterviewMessageEntity msg = new InterviewMessageEntity();
        msg.setId(messageId);
        msg.setInterviewId(interviewId);
        msg.setRole(MessageRoleEnum.AI);
        msg.setContent("请做自我介绍");
        msg.setQuestionType(QuestionTypeEnum.SELF_INTRO);
        msg.setSequenceNum(1);
        return msg;
    }
}
