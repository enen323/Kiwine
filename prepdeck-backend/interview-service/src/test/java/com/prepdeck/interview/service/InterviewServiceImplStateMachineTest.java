package com.prepdeck.interview.service;

import com.prepdeck.interview.client.QuotaClient;
import com.prepdeck.interview.client.ResumeClient;
import com.prepdeck.interview.domain.InterviewEntity;
import com.prepdeck.interview.domain.InterviewMessageEntity;
import com.prepdeck.interview.dto.request.SubmitAnswerRequest;
import com.prepdeck.interview.enums.InterviewStageEnum;
import com.prepdeck.interview.enums.InterviewStatusEnum;
import com.prepdeck.interview.enums.MessageRoleEnum;
import com.prepdeck.interview.enums.QuestionTypeEnum;
import com.prepdeck.interview.repository.InterviewMapper;
import com.prepdeck.interview.repository.InterviewMessageMapper;
import com.prepdeck.interview.repository.InterviewQuestionScoreMapper;
import com.prepdeck.interview.service.engine.InterviewEngine;
import com.prepdeck.common.exception.BadRequestException;
import com.prepdeck.common.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
// P1-1: 面试状态机流转 — CREATED→IN_PROGRESS→COMPLETED，各状态转换的边界条件
class InterviewServiceImplStateMachineTest {

    @Mock private InterviewMapper interviewMapper;
    @Mock private InterviewMessageMapper messageMapper;
    @Mock private InterviewQuestionScoreMapper questionScoreMapper;
    @Mock private InterviewEngine interviewEngine;
    @Mock private ResumeClient resumeClient;
    @Mock private QuotaClient quotaClient;
    @Mock private java.util.concurrent.Executor taskExecutor;
    @Mock private java.util.concurrent.ScheduledExecutorService interviewScheduler;
    @Mock
    private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

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
    // 将 taskExecutor.execute 改为同步执行，使 processAndStream 在测试线程内完成
    private void syncRunTaskExecutor() {
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(taskExecutor).execute(any(Runnable.class));
    }

    // ========== CREATED → IN_PROGRESS（首次回答） ==========

    // 测：面试状态为 CREATED，第一次 submitAnswer → 自动转为 IN_PROGRESS，记录 startedAt
    @Test
    void submitAnswer_firstAnswer_transitionsToInProgress() {
        InterviewEntity interview = createInterview(100L, 1L, InterviewStatusEnum.CREATED);
        when(interviewMapper.selectById(100L)).thenReturn(interview);
        when(messageMapper.selectMaxSeq(100L)).thenReturn(0);
        when(messageMapper.insert(any(InterviewMessageEntity.class))).thenReturn(1);
        when(messageMapper.findByInterviewId(anyLong(), anyInt())).thenReturn(List.of());

        // 引擎返回 stageCompleted=false（继续追问，面试未完成）
        InterviewEngine.EngineResult engineResult = new InterviewEngine.EngineResult(
                "追问内容", QuestionTypeEnum.SELF_INTRO, false, false, null,
                null, null, null, null, null, null, null
        );
        when(interviewEngine.processAnswerStream(any(), any(), any(), any(), any(SseEmitter.class))).thenReturn(engineResult);

        syncRunTaskExecutor();
        interviewService.submitAnswer(1L, 100L, new SubmitAnswerRequest("我的回答"));

        // 验证状态已转为 IN_PROGRESS（submitAnswer 同步更新 + processAndStream 异步更新，共 2 次）
        assertEquals(InterviewStatusEnum.IN_PROGRESS, interview.getStatus());
        assertNotNull(interview.getStartedAt());
        verify(interviewMapper, atLeast(2)).updateById(interview);
    }

    // ========== IN_PROGRESS 阶段内追问（状态不变） ==========

    // 测：面试已 IN_PROGRESS，引擎返回 stageCompleted=false → 状态保持 IN_PROGRESS，不完成
    @Test
    void submitAnswer_inProgress_stageNotCompleted_statusRemainsInProgress() {
        InterviewEntity interview = createInterview(100L, 1L, InterviewStatusEnum.IN_PROGRESS);
        when(interviewMapper.selectById(100L)).thenReturn(interview);
        when(messageMapper.selectMaxSeq(100L)).thenReturn(2);
        when(messageMapper.insert(any(InterviewMessageEntity.class))).thenReturn(1);
        when(messageMapper.findByInterviewId(anyLong(), anyInt())).thenReturn(List.of());

        InterviewEngine.EngineResult engineResult = new InterviewEngine.EngineResult(
                "追问内容", QuestionTypeEnum.SELF_INTRO, false, false, null,
                null, null, null, null, null, null, null
        );
        when(interviewEngine.processAnswerStream(any(), any(), any(), any(), any(SseEmitter.class))).thenReturn(engineResult);

        syncRunTaskExecutor();
        interviewService.submitAnswer(1L, 100L, new SubmitAnswerRequest("我的回答"));

        assertEquals(InterviewStatusEnum.IN_PROGRESS, interview.getStatus());
        verify(interviewMapper).updateById(interview);
    }

    // ========== 阶段完成 → 流转到下一阶段 ==========

    // 测：引擎返回 stageCompleted=true + nextStage=PROJECT_DEEP_DIVE → 阶段流转，stageOrder+1
    @Test
    void submitAnswer_stageCompleted_advancesToNextStage() {
        InterviewEntity interview = createInterview(100L, 1L, InterviewStatusEnum.IN_PROGRESS);
        interview.setCurrentStage(InterviewStageEnum.SELF_INTRO);
        interview.setStageOrder(0);
        when(interviewMapper.selectById(100L)).thenReturn(interview);
        when(messageMapper.selectMaxSeq(100L)).thenReturn(3); // ≥ MIN_EXCHANGES_PER_STAGE=3
        when(messageMapper.insert(any(InterviewMessageEntity.class))).thenReturn(1);
        when(messageMapper.findByInterviewId(anyLong(), anyInt())).thenReturn(List.of());

        InterviewEngine.EngineResult engineResult = new InterviewEngine.EngineResult(
                "进入下一阶段", QuestionTypeEnum.TECH_PROJECT, true, false, InterviewStageEnum.PROJECT_DEEP_DIVE,
                null, null, null, null, null, null, null
        );
        when(interviewEngine.processAnswerStream(any(), any(), any(), any(), any(SseEmitter.class))).thenReturn(engineResult);

        syncRunTaskExecutor();
        interviewService.submitAnswer(1L, 100L, new SubmitAnswerRequest("我的回答"));

        assertEquals(InterviewStageEnum.PROJECT_DEEP_DIVE, interview.getCurrentStage());
        assertEquals(1, interview.getStageOrder());
        verify(interviewMapper).updateById(interview);
    }

    // ========== 全部阶段完成 → COMPLETED ==========

    // 测：引擎返回 interviewCompleted=true → 状态转 COMPLETED，评分写入
    @Test
    void submitAnswer_allStagesCompleted_transitionsToCompleted() {
        InterviewEntity interview = createInterview(100L, 1L, InterviewStatusEnum.IN_PROGRESS);
        interview.setStartedAt(LocalDateTime.now().minusMinutes(30));
        when(interviewMapper.selectById(100L)).thenReturn(interview);
        when(messageMapper.selectMaxSeq(100L)).thenReturn(3);
        when(messageMapper.insert(any(InterviewMessageEntity.class))).thenReturn(1);
        when(messageMapper.findByInterviewId(anyLong(), anyInt())).thenReturn(List.of());

        InterviewEngine.EngineResult engineResult = new InterviewEngine.EngineResult(
                "面试结束，感谢参与。", null, true, true, InterviewStageEnum.COMPLETED,
                75, 80, 70, 75, 80, 70, "建议：多练习系统设计"
        );
        when(interviewEngine.processAnswerStream(any(), any(), any(), any(), any(SseEmitter.class))).thenReturn(engineResult);

        syncRunTaskExecutor();
        interviewService.submitAnswer(1L, 100L, new SubmitAnswerRequest("我的回答"));

        assertEquals(InterviewStatusEnum.COMPLETED, interview.getStatus());
        assertEquals(InterviewStageEnum.COMPLETED, interview.getCurrentStage());
        assertNull(interview.getScoreTotal()); // async aggregation not yet run
        assertNull(interview.getScoreTech());
        assertNull(interview.getScoreExpression());
        assertNotNull(interview.getCompletedAt());
        assertTrue(interview.getDurationSeconds() > 0);
        verify(interviewMapper).updateById(interview);
    }

    // ========== 终态不可再回答 ==========

    // 测：状态为 COMPLETED 时 submitAnswer → 抛 BadRequestException，零副作用
    @Test
    void submitAnswer_alreadyCompleted_throwsAndNoSideEffects() {
        InterviewEntity interview = createInterview(100L, 1L, InterviewStatusEnum.COMPLETED);
        when(interviewMapper.selectById(100L)).thenReturn(interview);

        assertThrows(BadRequestException.class,
                () -> interviewService.submitAnswer(1L, 100L, new SubmitAnswerRequest("回答")));

        verify(messageMapper, never()).insert(any(InterviewMessageEntity.class));
        verify(interviewMapper, never()).updateById(any(InterviewEntity.class));
    }

    // 测：状态为 TERMINATED 时 submitAnswer → 抛 BadRequestException，零副作用
    @Test
    void submitAnswer_alreadyTerminated_throwsAndNoSideEffects() {
        InterviewEntity interview = createInterview(100L, 1L, InterviewStatusEnum.TERMINATED);
        when(interviewMapper.selectById(100L)).thenReturn(interview);

        assertThrows(BadRequestException.class,
                () -> interviewService.submitAnswer(1L, 100L, new SubmitAnswerRequest("回答")));

        verify(messageMapper, never()).insert(any(InterviewMessageEntity.class));
        verify(interviewMapper, never()).updateById(any(InterviewEntity.class));
    }

    // ========== 越权提交 ==========

    // 测：用户B尝试回答用户A的面试 → 抛 NotFoundException，零副作用
    @Test
    void submitAnswer_wrongUser_throwsAndNoSideEffects() {
        InterviewEntity interview = createInterview(100L, 2L, InterviewStatusEnum.IN_PROGRESS);
        when(interviewMapper.selectById(100L)).thenReturn(interview);

        assertThrows(NotFoundException.class,
                () -> interviewService.submitAnswer(1L, 100L, new SubmitAnswerRequest("回答")));

        verify(messageMapper, never()).insert(any(InterviewMessageEntity.class));
        verify(interviewMapper, never()).updateById(any(InterviewEntity.class));
    }

    // ========== P1-3 补充：sequenceNum 递增验证 ==========

    // 测：引擎抛异常 → emitter.completeWithError 被调用，面试状态不更新
    @Test
    void submitAnswer_engineThrows_emitterCompletesWithError() {
        InterviewEntity interview = createInterview(100L, 1L, InterviewStatusEnum.IN_PROGRESS);
        when(interviewMapper.selectById(100L)).thenReturn(interview);
        when(messageMapper.selectMaxSeq(100L)).thenReturn(1);
        when(messageMapper.insert(any(InterviewMessageEntity.class))).thenReturn(1);
        when(messageMapper.findByInterviewId(anyLong(), anyInt())).thenReturn(List.of());

        when(interviewEngine.processAnswerStream(any(), any(), any(), any(), any(SseEmitter.class)))
                .thenThrow(new RuntimeException("LLM service error"));

        syncRunTaskExecutor();
        // 不应向上抛异常（内部 catch 后用 emitter.completeWithError）
        assertDoesNotThrow(() -> interviewService.submitAnswer(1L, 100L, new SubmitAnswerRequest("回答")));

        // 面试状态不应被改为 COMPLETED
        assertEquals(InterviewStatusEnum.IN_PROGRESS, interview.getStatus());
    }

    // ========== 阶段流转边界：REVERSE_QA 完成后直接 COMPLETED ==========

    // 测：REVERSE_QA 阶段完成，next() 返回 COMPLETED → 直接完成面试
    @Test
    void submitAnswer_reverseQaCompleted_transitionsDirectlyToCompleted() {
        InterviewEntity interview = createInterview(100L, 1L, InterviewStatusEnum.IN_PROGRESS);
        interview.setCurrentStage(InterviewStageEnum.REVERSE_QA);
        interview.setStageOrder(3);
        when(interviewMapper.selectById(100L)).thenReturn(interview);
        when(messageMapper.selectMaxSeq(100L)).thenReturn(3);
        when(messageMapper.insert(any(InterviewMessageEntity.class))).thenReturn(1);
        when(messageMapper.findByInterviewId(anyLong(), anyInt())).thenReturn(List.of());

        // 引擎返回 nextStage=COMPLETED，interviewCompleted=true
        InterviewEngine.EngineResult engineResult = new InterviewEngine.EngineResult(
                "谢谢面试官", QuestionTypeEnum.REVERSE_QA, true, true, InterviewStageEnum.COMPLETED,
                85, 85, 80, 85, 85, 75, "表现优秀"
        );
        when(interviewEngine.processAnswerStream(any(), any(), any(), any(), any(SseEmitter.class))).thenReturn(engineResult);

        syncRunTaskExecutor();
        interviewService.submitAnswer(1L, 100L, new SubmitAnswerRequest("我的问题"));

        assertEquals(InterviewStatusEnum.COMPLETED, interview.getStatus());
        assertEquals(InterviewStageEnum.COMPLETED, interview.getCurrentStage());
        assertNull(interview.getScoreTotal()); // async aggregation not yet run
    }

    // ========== P1-3 补充：sequenceNum 递增验证 ==========

    // 测：无历史消息，用户回答的 sequenceNum=1（getNextSequence: 0+1=1）
    @Test
    void submitAnswer_noHistory_userSequenceNumIsOne() {
        InterviewEntity interview = createInterview(100L, 1L, InterviewStatusEnum.CREATED);
        when(interviewMapper.selectById(100L)).thenReturn(interview);
        when(messageMapper.selectMaxSeq(100L)).thenReturn(0);
        when(messageMapper.insert(any(InterviewMessageEntity.class))).thenReturn(1);
        when(messageMapper.findByInterviewId(anyLong(), anyInt())).thenReturn(List.of());

        InterviewEngine.EngineResult engineResult = new InterviewEngine.EngineResult(
                "AI response", QuestionTypeEnum.SELF_INTRO, false, false, null, null, null, null, null, null, null, null);
        when(interviewEngine.processAnswerStream(any(), any(), any(), any(), any(SseEmitter.class))).thenReturn(engineResult);

        syncRunTaskExecutor();
        interviewService.submitAnswer(1L, 100L, new SubmitAnswerRequest("my answer"));

        // 验证 capturedInterview（即传入 selectById 的实体）的 totalQuestions 已递增
        assertEquals(1, interview.getTotalQuestions());
    }

    // 测：已有5条消息（count=5），新用户回答 sequenceNum=6（5+1=6）
    @Test
    void submitAnswer_fiveExistingMessages_userSequenceNumIsSix() {
        InterviewEntity interview = createInterview(100L, 1L, InterviewStatusEnum.IN_PROGRESS);
        when(interviewMapper.selectById(100L)).thenReturn(interview);
        when(messageMapper.selectMaxSeq(100L)).thenReturn(5);
        when(messageMapper.insert(any(InterviewMessageEntity.class))).thenReturn(1);
        when(messageMapper.findByInterviewId(anyLong(), anyInt())).thenReturn(List.of());

        InterviewEngine.EngineResult engineResult = new InterviewEngine.EngineResult(
                "AI response", QuestionTypeEnum.SELF_INTRO, false, false, null, null, null, null, null, null, null, null);
        when(interviewEngine.processAnswerStream(any(), any(), any(), any(), any(SseEmitter.class))).thenReturn(engineResult);

        syncRunTaskExecutor();
        interviewService.submitAnswer(1L, 100L, new SubmitAnswerRequest("my answer"));

        // totalQuestions 从 0 变为 1，验证消息已保存
        assertEquals(1, interview.getTotalQuestions());
    }

    // ========== 辅助方法 ==========

    private InterviewEntity createInterview(Long id, Long userId, InterviewStatusEnum status) {
        InterviewEntity interview = new InterviewEntity();
        interview.setId(id);
        interview.setUserId(userId);
        interview.setCompany("ALIBABA");
        interview.setPosition("BACKEND");
        interview.setStatus(status);
        interview.setCurrentStage(InterviewStageEnum.SELF_INTRO);
        interview.setStageOrder(0);
        interview.setTotalQuestions(0);
        return interview;
    }
}
