package com.prepdeck.interview.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.prepdeck.common.dto.QuotaCheckResult;
import com.prepdeck.common.enums.ErrorCode;
import com.prepdeck.common.exception.BadRequestException;
import com.prepdeck.common.exception.BusinessException;
import com.prepdeck.common.exception.NotFoundException;
import com.prepdeck.common.model.PageResult;
import com.prepdeck.common.model.Result;
import com.prepdeck.interview.client.QuotaClient;
import com.prepdeck.interview.client.ResumeClient;
import com.prepdeck.interview.domain.InterviewEntity;
import com.prepdeck.interview.domain.InterviewMessageEntity;
import com.prepdeck.interview.dto.request.StartInterviewRequest;
import com.prepdeck.interview.dto.request.SubmitAnswerRequest;
import com.prepdeck.interview.dto.response.*;
import com.prepdeck.interview.enums.*;
import com.prepdeck.interview.repository.InterviewMapper;
import com.prepdeck.interview.repository.InterviewMessageMapper;
import com.prepdeck.interview.repository.InterviewQuestionScoreMapper;
import com.prepdeck.interview.service.engine.InterviewEngine;
import com.prepdeck.resume.dto.response.ResumeVO;
import com.prepdeck.resume.dto.response.SectionVO;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class InterviewServiceImplTest {

    @Mock
    private InterviewMapper interviewMapper;
    @Mock
    private InterviewMessageMapper messageMapper;
    @Mock
    private InterviewQuestionScoreMapper questionScoreMapper;
    @Mock
    private InterviewEngine interviewEngine;
    @Mock
    private ResumeClient resumeClient;
    @Mock
    private Executor taskExecutor;
    @Mock
    private java.util.concurrent.ScheduledExecutorService interviewScheduler;
    @Mock
    private QuotaClient quotaClient;
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
        org.mockito.Mockito.lenient().when(quotaClient.consume(anyString()))
                .thenReturn(Result.success(QuotaCheckResult.allowed("FREE")));

        interviewService = new InterviewServiceImpl(interviewMapper, messageMapper, questionScoreMapper,
                interviewEngine, resumeClient, taskExecutor, interviewScheduler, quotaClient, stringRedisTemplate);
    }

    // ========== startInterview ==========

    @Test
    void startInterviewSuccess() {
        when(quotaClient.consume(eq("INTERVIEW"))).thenReturn(Result.success(QuotaCheckResult.allowed("FREE")));
        when(interviewMapper.selectCount(any())).thenReturn(0L);
        doAnswer(inv -> {
            InterviewEntity e = inv.getArgument(0);
            e.setId(100L);
            return 1;
        }).when(interviewMapper).insert(any(InterviewEntity.class));

        InterviewMessageEntity firstMsg = new InterviewMessageEntity();
        firstMsg.setId(200L);
        firstMsg.setInterviewId(100L);
        firstMsg.setRole(MessageRoleEnum.AI);
        firstMsg.setContent("请做自我介绍");
        firstMsg.setQuestionType(QuestionTypeEnum.SELF_INTRO);
        firstMsg.setSequenceNum(1);
        when(interviewEngine.generateFirstQuestion(any(), any())).thenReturn(firstMsg);
        when(messageMapper.insert(any(InterviewMessageEntity.class))).thenReturn(1);

        StartInterviewRequest request = new StartInterviewRequest(CompanyEnum.ALIBABA, PositionEnum.BACKEND, null);
        StartInterviewResponse response = interviewService.startInterview(1L, request);

        assertNotNull(response);
        assertNotNull(response.interview());
        assertNotNull(response.firstQuestion());
        assertEquals(100L, response.interview().id());
        assertEquals(200L, response.firstQuestion().id());
        assertEquals(InterviewStatusEnum.CREATED, response.interview().status());
    }

    @Test
    void startInterviewFailsWhenActiveInterviewExists() {
        when(quotaClient.consume(eq("INTERVIEW"))).thenReturn(Result.success(QuotaCheckResult.allowed("FREE")));
        when(interviewMapper.selectCount(any())).thenReturn(1L);

        StartInterviewRequest request = new StartInterviewRequest(CompanyEnum.ALIBABA, PositionEnum.BACKEND, null);
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> interviewService.startInterview(1L, request));
        assertTrue(ex.getMessage().contains("进行中的面试"));
    }

    @Test
    void startInterviewFailsWhenQuotaExhausted() {
        when(quotaClient.consume(eq("INTERVIEW")))
                .thenReturn(Result.success(QuotaCheckResult.denied("QUOTA_EXHAUSTED", null)));

        StartInterviewRequest request = new StartInterviewRequest(CompanyEnum.ALIBABA, PositionEnum.BACKEND, null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> interviewService.startInterview(1L, request));
        assertEquals(ErrorCode.QUOTA_EXHAUSTED.getCode(), ex.getCode());

        verify(interviewMapper, never()).insert(any(InterviewEntity.class));
        verify(messageMapper, never()).insert(any(InterviewMessageEntity.class));
        verify(interviewEngine, never()).generateFirstQuestion(any(), any());
    }

    @Test
    void startInterview_loadsResumeProfile() {
        when(quotaClient.consume(eq("INTERVIEW"))).thenReturn(Result.success(QuotaCheckResult.allowed("FREE")));
        ResumeVO resumeVO = resumeVOWithSections();
        when(resumeClient.getById(100L)).thenReturn(resumeVO);
        when(interviewMapper.selectCount(any())).thenReturn(0L);
        doAnswer(inv -> {
            InterviewEntity e = inv.getArgument(0);
            e.setId(1L);
            return 1;
        }).when(interviewMapper).insert(any(InterviewEntity.class));

        InterviewMessageEntity firstMsg = new InterviewMessageEntity();
        firstMsg.setId(200L);
        firstMsg.setInterviewId(1L);
        firstMsg.setRole(MessageRoleEnum.AI);
        firstMsg.setContent("请做自我介绍");
        firstMsg.setQuestionType(QuestionTypeEnum.SELF_INTRO);
        firstMsg.setSequenceNum(1);
        when(interviewEngine.generateFirstQuestion(any(), any())).thenReturn(firstMsg);
        when(messageMapper.insert(any(InterviewMessageEntity.class))).thenReturn(1);

        StartInterviewRequest request = new StartInterviewRequest(CompanyEnum.ALIBABA, PositionEnum.BACKEND, 100L);
        StartInterviewResponse response = interviewService.startInterview(1L, request);

        verify(resumeClient).getById(100L);
        assertNotNull(response);
        assertNotNull(response.resumeProfile());
        assertEquals(1L, response.interview().id());
        assertEquals(200L, response.firstQuestion().id());
    }

    // ========== submitAnswer ==========

    @Test
    void submitAnswerFirstAnswerTransitionsToInProgress() {
        InterviewEntity interview = createInterview(100L, 1L, InterviewStatusEnum.CREATED);
        when(interviewMapper.selectById(100L)).thenReturn(interview);
        when(messageMapper.selectMaxSeq(100L)).thenReturn(0);
        when(messageMapper.insert(any(InterviewMessageEntity.class))).thenReturn(1);
        when(messageMapper.findByInterviewId(anyLong(), anyInt())).thenReturn(List.of());

        InterviewEngine.EngineResult engineResult = new InterviewEngine.EngineResult(
                "AI response", QuestionTypeEnum.SELF_INTRO, false, false, null, null, null, null, null, null, null, null);
        when(interviewEngine.processAnswerStream(any(), any(), any(), any(), any(SseEmitter.class))).thenReturn(engineResult);

        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(taskExecutor).execute(any(Runnable.class));

        SubmitAnswerRequest request = new SubmitAnswerRequest("my answer");
        SseEmitter emitter = interviewService.submitAnswer(1L, 100L, request);

        assertNotNull(emitter);
    }

    @Test
    void submitAnswerSubsequentAnswer() {
        InterviewEntity interview = createInterview(100L, 1L, InterviewStatusEnum.IN_PROGRESS);
        when(interviewMapper.selectById(100L)).thenReturn(interview);
        when(messageMapper.selectMaxSeq(100L)).thenReturn(3);
        when(messageMapper.insert(any(InterviewMessageEntity.class))).thenReturn(1);
        when(messageMapper.findByInterviewId(anyLong(), anyInt())).thenReturn(List.of());

        InterviewEngine.EngineResult engineResult = new InterviewEngine.EngineResult(
                "AI response", QuestionTypeEnum.SELF_INTRO, false, false, null, null, null, null, null, null, null, null);
        when(interviewEngine.processAnswerStream(any(), any(), any(), any(), any(SseEmitter.class))).thenReturn(engineResult);

        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(taskExecutor).execute(any(Runnable.class));

        SubmitAnswerRequest request = new SubmitAnswerRequest("my answer");
        SseEmitter emitter = interviewService.submitAnswer(1L, 100L, request);

        assertNotNull(emitter);
    }

    @Test
    void submitAnswerInterviewNotFound() {
        when(interviewMapper.selectById(999L)).thenReturn(null);

        SubmitAnswerRequest request = new SubmitAnswerRequest("answer");
        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> interviewService.submitAnswer(1L, 999L, request));
        assertEquals(ErrorCode.INTERVIEW_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void submitAnswerWrongUser() {
        InterviewEntity interview = createInterview(100L, 2L, InterviewStatusEnum.IN_PROGRESS);
        when(interviewMapper.selectById(100L)).thenReturn(interview);

        SubmitAnswerRequest request = new SubmitAnswerRequest("answer");
        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> interviewService.submitAnswer(1L, 100L, request));
        assertEquals(ErrorCode.INTERVIEW_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void submitAnswerAlreadyCompleted() {
        InterviewEntity interview = createInterview(100L, 1L, InterviewStatusEnum.COMPLETED);
        when(interviewMapper.selectById(100L)).thenReturn(interview);

        SubmitAnswerRequest request = new SubmitAnswerRequest("answer");
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> interviewService.submitAnswer(1L, 100L, request));
        assertEquals(ErrorCode.INTERVIEW_ALREADY_COMPLETED.getCode(), ex.getCode());
    }

    @Test
    void submitAnswerAlreadyTerminated() {
        InterviewEntity interview = createInterview(100L, 1L, InterviewStatusEnum.TERMINATED);
        when(interviewMapper.selectById(100L)).thenReturn(interview);

        SubmitAnswerRequest request = new SubmitAnswerRequest("answer");
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> interviewService.submitAnswer(1L, 100L, request));
        assertEquals(ErrorCode.INTERVIEW_ALREADY_COMPLETED.getCode(), ex.getCode());
    }

    // ========== P1-3 补充：sequenceNum 递增与 processAndStream 异常 ==========

    // 测：无历史消息，count=0 → 新消息 sequenceNum=1
    @Test
    void submitAnswer_noExistingMessages_sequenceNumIsOne() {
        InterviewEntity interview = createInterview(100L, 1L, InterviewStatusEnum.CREATED);
        when(interviewMapper.selectById(100L)).thenReturn(interview);
        when(messageMapper.selectMaxSeq(100L)).thenReturn(0);

        ArgumentCaptor<InterviewMessageEntity> captor = ArgumentCaptor.forClass(InterviewMessageEntity.class);
        when(messageMapper.insert(captor.capture())).thenReturn(1);
        when(messageMapper.findByInterviewId(anyLong(), anyInt())).thenReturn(List.of());

        InterviewEngine.EngineResult engineResult = new InterviewEngine.EngineResult(
                "AI response", QuestionTypeEnum.SELF_INTRO, false, false, null, null, null, null, null, null, null, null);
        when(interviewEngine.processAnswerStream(any(), any(), any(), any(), any(SseEmitter.class))).thenReturn(engineResult);

        syncRunTaskExecutor();
        interviewService.submitAnswer(1L, 100L, new SubmitAnswerRequest("my answer"));

        assertEquals(2, captor.getValue().getSequenceNum());
        assertEquals(MessageRoleEnum.AI, captor.getValue().getRole());
        assertEquals("AI response", captor.getValue().getContent());
    }

    // 测：已有5条消息，新消息 sequenceNum=6（count+1）
    @Test
    void submitAnswer_withFiveMessages_sequenceNumIsSix() {
        InterviewEntity interview = createInterview(100L, 1L, InterviewStatusEnum.IN_PROGRESS);
        when(interviewMapper.selectById(100L)).thenReturn(interview);
        when(messageMapper.selectMaxSeq(100L)).thenReturn(5);

        ArgumentCaptor<InterviewMessageEntity> captor = ArgumentCaptor.forClass(InterviewMessageEntity.class);
        when(messageMapper.insert(captor.capture())).thenReturn(1);
        when(messageMapper.findByInterviewId(anyLong(), anyInt())).thenReturn(List.of());

        InterviewEngine.EngineResult engineResult = new InterviewEngine.EngineResult(
                "AI response", QuestionTypeEnum.SELF_INTRO, false, false, null, null, null, null, null, null, null, null);
        when(interviewEngine.processAnswerStream(any(), any(), any(), any(), any(SseEmitter.class))).thenReturn(engineResult);

        syncRunTaskExecutor();
        interviewService.submitAnswer(1L, 100L, new SubmitAnswerRequest("my answer"));

        assertEquals(7, captor.getValue().getSequenceNum());
    }

    // 测：首次回答（CREATED）→ 记录 startedAt，状态转 IN_PROGRESS
    @Test
    void submitAnswer_firstAnswer_setsStartedAtAndTransitionsToInProgress() {
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

        assertEquals(InterviewStatusEnum.IN_PROGRESS, interview.getStatus());
        assertNotNull(interview.getStartedAt());
    }

    // 测：非首次回答（IN_PROGRESS）→ 不改变 startedAt（保留原始开始时间）
    @Test
    void submitAnswer_subsequentAnswer_preservesStartedAt() {
        LocalDateTime existingStart = LocalDateTime.now().minusHours(1);
        InterviewEntity interview = createInterview(100L, 1L, InterviewStatusEnum.IN_PROGRESS);
        interview.setStartedAt(existingStart);
        when(interviewMapper.selectById(100L)).thenReturn(interview);
        when(messageMapper.selectMaxSeq(100L)).thenReturn(2);
        when(messageMapper.insert(any(InterviewMessageEntity.class))).thenReturn(1);
        when(messageMapper.findByInterviewId(anyLong(), anyInt())).thenReturn(List.of());

        InterviewEngine.EngineResult engineResult = new InterviewEngine.EngineResult(
                "AI response", QuestionTypeEnum.SELF_INTRO, false, false, null, null, null, null, null, null, null, null);
        when(interviewEngine.processAnswerStream(any(), any(), any(), any(), any(SseEmitter.class))).thenReturn(engineResult);

        syncRunTaskExecutor();
        interviewService.submitAnswer(1L, 100L, new SubmitAnswerRequest("my answer"));

        assertEquals(existingStart, interview.getStartedAt());
    }

    // 测：processAnswerStream 抛异常 → 内部 catch，emitter.completeWithError，不向上抛
    @Test
    void submitAnswer_engineThrows_doesNotPropagateAndDoesNotUpdateStatus() {
        InterviewEntity interview = createInterview(100L, 1L, InterviewStatusEnum.IN_PROGRESS);
        when(interviewMapper.selectById(100L)).thenReturn(interview);
        when(messageMapper.selectMaxSeq(100L)).thenReturn(1);
        when(messageMapper.insert(any(InterviewMessageEntity.class))).thenReturn(1);
        when(messageMapper.findByInterviewId(anyLong(), anyInt())).thenReturn(List.of());

        when(interviewEngine.processAnswerStream(any(), any(), any(), any(), any(SseEmitter.class)))
                .thenThrow(new RuntimeException("LLM service error"));

        syncRunTaskExecutor();
        assertDoesNotThrow(() -> interviewService.submitAnswer(1L, 100L, new SubmitAnswerRequest("my answer")));

        // 引擎异常不更新面试状态
        assertEquals(InterviewStatusEnum.IN_PROGRESS, interview.getStatus());
    }

    // 测：用户回答先于引擎调用入库（消息保存顺序：用户消息 → 引擎 → AI消息）
    @Test
    void submitAnswer_userMessageInsertedBeforeEngine() {
        InterviewEntity interview = createInterview(100L, 1L, InterviewStatusEnum.IN_PROGRESS);
        when(interviewMapper.selectById(100L)).thenReturn(interview);
        when(messageMapper.selectMaxSeq(100L)).thenReturn(1);

        ArgumentCaptor<InterviewMessageEntity> captor = ArgumentCaptor.forClass(InterviewMessageEntity.class);
        when(messageMapper.insert(captor.capture())).thenReturn(1);
        when(messageMapper.findByInterviewId(anyLong(), anyInt())).thenReturn(List.of());

        InterviewEngine.EngineResult engineResult = new InterviewEngine.EngineResult(
                "AI response", QuestionTypeEnum.SELF_INTRO, false, false, null, null, null, null, null, null, null, null);
        when(interviewEngine.processAnswerStream(any(), any(), any(), any(), any(SseEmitter.class))).thenReturn(engineResult);

        syncRunTaskExecutor();
        interviewService.submitAnswer(1L, 100L, new SubmitAnswerRequest("my answer"));

        // 验证：两次 insert 调用（用户消息 + AI消息），第一次是 USER
        List<InterviewMessageEntity> allInserted = captor.getAllValues();
        assertEquals(2, allInserted.size());
        assertEquals(MessageRoleEnum.USER, allInserted.getFirst().getRole());
        assertEquals("my answer", allInserted.getFirst().getContent());
        // 验证引擎在用户消息插入后被调用
        verify(interviewEngine).processAnswerStream(any(), any(), any(), any(), any(SseEmitter.class));
    }

    @Test
    void submitAnswer_returnsSseEmitter() {
        InterviewEntity interview = createInterview(100L, 1L, InterviewStatusEnum.CREATED);
        interview.setResumeId(200L);
        when(interviewMapper.selectById(100L)).thenReturn(interview);
        when(messageMapper.selectMaxSeq(100L)).thenReturn(0);
        when(messageMapper.insert(any(InterviewMessageEntity.class))).thenReturn(1);
        when(messageMapper.findByInterviewId(anyLong(), anyInt())).thenReturn(List.of());

        ResumeVO resumeVO = resumeVOWithSections();
        when(resumeClient.getById(200L)).thenReturn(resumeVO);

        InterviewEngine.EngineResult engineResult = new InterviewEngine.EngineResult(
                "AI response", QuestionTypeEnum.SELF_INTRO, false, false, null, null, null, null, null, null, null, null);
        when(interviewEngine.processAnswerStream(any(), any(), any(), any(), any(SseEmitter.class))).thenReturn(engineResult);
        when(interviewEngine.scoreQuestion(anyString(), any(QuestionTypeEnum.class), anyString()))
                .thenReturn(new InterviewEngine.QuestionScore("desc", 8, "GOOD", "good"));

        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(taskExecutor).execute(any(Runnable.class));

        SubmitAnswerRequest request = new SubmitAnswerRequest("my answer");
        SseEmitter emitter = interviewService.submitAnswer(1L, 100L, request);

        assertNotNull(emitter);
        verify(resumeClient).getById(200L);
    }

    // ========== P0-4: 反问环节 1 轮即完成 ==========

    // 测：反问环节只需 1 轮 AI 消息即可完成面试，面试状态立即更新，
    //     但总分由异步聚合延迟生成，因此此时 scoreTotal 仍为 null
    @Test
    void submitAnswer_reverseQa_oneRound_completesInterview_andSchedulesAggregation() {
        InterviewEntity interview = createInterview(100L, 1L, InterviewStatusEnum.IN_PROGRESS);
        interview.setCurrentStage(InterviewStageEnum.REVERSE_QA);
        when(interviewMapper.selectById(100L)).thenReturn(interview);
        when(messageMapper.selectMaxSeq(100L)).thenReturn(1);
        when(messageMapper.insert(any(InterviewMessageEntity.class))).thenReturn(1);
        when(messageMapper.findByInterviewId(anyLong(), anyInt())).thenReturn(List.of());

        InterviewEngine.EngineResult engineResult = new InterviewEngine.EngineResult(
                "面试结束，感谢参与。", QuestionTypeEnum.REVERSE_QA, true, true, InterviewStageEnum.COMPLETED,
                null, null, null, null, null, null, null
        );
        when(interviewEngine.processAnswerStream(any(), any(), any(), any(), any(SseEmitter.class))).thenReturn(engineResult);

        syncRunTaskExecutor();
        interviewService.submitAnswer(1L, 100L, new SubmitAnswerRequest("我的问题"));

        assertEquals(InterviewStatusEnum.COMPLETED, interview.getStatus());
        assertEquals(InterviewStageEnum.COMPLETED, interview.getCurrentStage());
        assertNull(interview.getScoreTotal()); // 异步聚合尚未执行
        assertNotNull(interview.getCompletedAt());

        verify(interviewScheduler).schedule(any(Runnable.class), eq(10L), eq(TimeUnit.SECONDS));
    }

    // ========== getInterview ==========

    @Test
    void getInterviewSuccess() {
        InterviewEntity interview = createInterview(100L, 1L, InterviewStatusEnum.IN_PROGRESS);
        when(interviewMapper.selectById(100L)).thenReturn(interview);

        InterviewVO vo = interviewService.getInterview(1L, 100L);

        assertNotNull(vo);
        assertEquals(100L, vo.id());
        assertEquals(1L, vo.userId());
    }

    @Test
    void getInterviewNotFound() {
        when(interviewMapper.selectById(999L)).thenReturn(null);

        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> interviewService.getInterview(1L, 999L));
        assertEquals(ErrorCode.INTERVIEW_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void getInterviewWrongUser() {
        InterviewEntity interview = createInterview(100L, 2L, InterviewStatusEnum.IN_PROGRESS);
        when(interviewMapper.selectById(100L)).thenReturn(interview);

        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> interviewService.getInterview(1L, 100L));
        assertEquals(ErrorCode.INTERVIEW_NOT_FOUND.getCode(), ex.getCode());
    }

    // ========== listUserInterviews ==========

    @Test
    void listUserInterviewsWithoutFilters() {
        InterviewEntity interview = createInterview(100L, 1L, InterviewStatusEnum.IN_PROGRESS);
        Page<InterviewEntity> page = new Page<>(1, 10);
        page.setRecords(List.of(interview));
        page.setTotal(1);
        when(interviewMapper.selectPage(any(Page.class), any())).thenReturn(page);

        PageResult<InterviewListVO> result = interviewService.listUserInterviews(1L, null, null, 1, 10);

        assertNotNull(result);
        assertEquals(1, result.data().size());
        assertEquals(100L, result.data().get(0).id());
    }

    @Test
    void listUserInterviewsWithFilters() {
        InterviewEntity interview = createInterview(100L, 1L, InterviewStatusEnum.IN_PROGRESS);
        Page<InterviewEntity> page = new Page<>(1, 10);
        page.setRecords(List.of(interview));
        page.setTotal(1);
        when(interviewMapper.selectPage(any(Page.class), any())).thenReturn(page);

        PageResult<InterviewListVO> result = interviewService.listUserInterviews(1L, "ALIBABA", "IN_PROGRESS", 1, 10);

        assertEquals(1, result.data().size());
    }

    // ========== getMessages ==========

    @Test
    void getMessagesSuccess() {
        InterviewEntity interview = createInterview(100L, 1L, InterviewStatusEnum.IN_PROGRESS);
        when(interviewMapper.selectById(100L)).thenReturn(interview);

        InterviewMessageEntity msg = new InterviewMessageEntity();
        msg.setId(1L);
        msg.setInterviewId(100L);
        msg.setRole(MessageRoleEnum.AI);
        msg.setContent("Hello");
        msg.setSequenceNum(1);
        when(messageMapper.findByInterviewId(anyLong(), anyInt())).thenReturn(List.of(msg));

        List<InterviewMessageVO> messages = interviewService.getMessages(1L, 100L);

        assertEquals(1, messages.size());
        assertEquals("Hello", messages.get(0).content());
    }

    @Test
    void getMessagesInterviewNotFound() {
        when(interviewMapper.selectById(999L)).thenReturn(null);

        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> interviewService.getMessages(1L, 999L));
        assertEquals(ErrorCode.INTERVIEW_NOT_FOUND.getCode(), ex.getCode());
    }

    // ========== terminateInterview ==========

    @Test
    void terminateInterviewSuccess() {
        InterviewEntity interview = createInterview(100L, 1L, InterviewStatusEnum.IN_PROGRESS);
        when(interviewMapper.selectById(100L)).thenReturn(interview);

        interviewService.terminateInterview(1L, 100L);

        ArgumentCaptor<InterviewEntity> captor = ArgumentCaptor.forClass(InterviewEntity.class);
        verify(interviewMapper).updateById(captor.capture());
        assertEquals(InterviewStatusEnum.TERMINATED, captor.getValue().getStatus());
    }

    @Test
    void terminateInterviewAlreadyCompleted() {
        InterviewEntity interview = createInterview(100L, 1L, InterviewStatusEnum.COMPLETED);
        when(interviewMapper.selectById(100L)).thenReturn(interview);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> interviewService.terminateInterview(1L, 100L));
        assertEquals(ErrorCode.INTERVIEW_ALREADY_COMPLETED.getCode(), ex.getCode());
    }

    @Test
    void terminateInterviewAlreadyTerminated() {
        InterviewEntity interview = createInterview(100L, 1L, InterviewStatusEnum.TERMINATED);
        when(interviewMapper.selectById(100L)).thenReturn(interview);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> interviewService.terminateInterview(1L, 100L));
        assertEquals(ErrorCode.INTERVIEW_ALREADY_COMPLETED.getCode(), ex.getCode());
    }

    @Test
    void deleteInterviewSuccess_completedSoftDeletes() {
        InterviewEntity interview = createInterview(100L, 1L, InterviewStatusEnum.COMPLETED);
        when(interviewMapper.selectById(100L)).thenReturn(interview);

        interviewService.deleteInterview(1L, 100L);

        verify(messageMapper).update(any(InterviewMessageEntity.class), any());
        verify(interviewMapper).updateById(any(InterviewEntity.class));
        verify(interviewMapper, never()).deleteById(anyLong());
        verify(messageMapper, never()).delete(any());
    }

    @Test
    void deleteInterviewSuccess_otherStatusHardDeletes() {
        InterviewEntity interview = createInterview(100L, 1L, InterviewStatusEnum.IN_PROGRESS);
        when(interviewMapper.selectById(100L)).thenReturn(interview);

        interviewService.deleteInterview(1L, 100L);

        verify(questionScoreMapper).hardDeleteByInterviewId(eq(100L));
        verify(messageMapper).hardDeleteByInterviewId(eq(100L));
        verify(interviewMapper).hardDeleteById(eq(100L));
        verify(interviewMapper, never()).updateById(any(InterviewEntity.class));
    }

    @Test
    void deleteInterviewNotFound() {
        when(interviewMapper.selectById(999L)).thenReturn(null);

        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> interviewService.deleteInterview(1L, 999L));
        assertEquals(ErrorCode.INTERVIEW_NOT_FOUND.getCode(), ex.getCode());

        verify(messageMapper, never()).delete(any());
        verify(interviewMapper, never()).deleteById(anyLong());
    }

    @Test
    void deleteInterviewWrongUser() {
        InterviewEntity interview = createInterview(100L, 2L, InterviewStatusEnum.IN_PROGRESS);
        when(interviewMapper.selectById(100L)).thenReturn(interview);

        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> interviewService.deleteInterview(1L, 100L));
        assertEquals(ErrorCode.INTERVIEW_NOT_FOUND.getCode(), ex.getCode());

        verify(messageMapper, never()).delete(any());
        verify(interviewMapper, never()).deleteById(anyLong());
    }

    // ========== P2-1 补充：deleteInterview 软删除/硬删除精确验证 ==========

    // 测：COMPLETED 面试软删除 → interview.deleted=true，message.deleted=true
    @Test
    void deleteInterview_completed_softDeletesInterviewAndMessages() {
        InterviewEntity interview = createInterview(100L, 1L, InterviewStatusEnum.COMPLETED);
        when(interviewMapper.selectById(100L)).thenReturn(interview);

        interviewService.deleteInterview(1L, 100L);

        // 验证 interviewMapper.updateById 被调用（软删除）
        verify(interviewMapper).updateById(any(InterviewEntity.class));
        verify(interviewMapper, never()).deleteById(anyLong());

        // 验证 messageMapper.update 被调用以软删除消息（传入 deleted=true 的 wrapper）
        verify(messageMapper).update(any(InterviewMessageEntity.class), any());
        verify(messageMapper, never()).delete(any());
    }

    // 测：TERMINATED 面试 → 硬删除（与 CREATED/IN_PROGRESS 一致，物理删除）
    @Test
    void deleteInterview_terminated_softDeletes() {
        InterviewEntity interview = createInterview(100L, 1L, InterviewStatusEnum.TERMINATED);
        when(interviewMapper.selectById(100L)).thenReturn(interview);

        interviewService.deleteInterview(1L, 100L);

        verify(questionScoreMapper).hardDeleteByInterviewId(eq(100L));
        verify(messageMapper).hardDeleteByInterviewId(eq(100L));
        verify(interviewMapper).hardDeleteById(eq(100L));
        verify(interviewMapper, never()).updateById(any(InterviewEntity.class));
        verify(messageMapper, never()).update(any(), any());
    }

    // 测：CREATED（未开始）面试 → 硬删除（可撤销的面试直接物理删除）
    @Test
    void deleteInterview_created_hardDeletes() {
        InterviewEntity interview = createInterview(100L, 1L, InterviewStatusEnum.CREATED);
        when(interviewMapper.selectById(100L)).thenReturn(interview);

        interviewService.deleteInterview(1L, 100L);

        verify(questionScoreMapper).hardDeleteByInterviewId(eq(100L));
        verify(messageMapper).hardDeleteByInterviewId(eq(100L));
        verify(interviewMapper).hardDeleteById(eq(100L));
    }

    @Test
    void controlInterview_hintReturnsSseEmitter() {
        InterviewEntity interview = createInterview(100L, 1L, InterviewStatusEnum.IN_PROGRESS);
        when(interviewMapper.selectById(100L)).thenReturn(interview);
        when(messageMapper.findByInterviewId(anyLong(), anyInt())).thenReturn(List.of());
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(taskExecutor).execute(any(Runnable.class));
        when(interviewEngine.processControlCommand(any(), any(), anyString(), anyList(), any()))
                .thenAnswer(invocation -> {
                    SseEmitter emitter = invocation.getArgument(4);
                    emitter.send(SseEmitter.event().name("chunk").data("提示内容"));
                    return new InterviewEngine.EngineResult("提示内容", null, false, false, null, null, null, null, null, null, null, null);
                });

        SseEmitter emitter = interviewService.controlInterview(1L, 100L, ControlCommand.HINT);
        assertNotNull(emitter);
    }

    // ========== P0-3: 异步任务串行化 ==========

    // 测：getNextSequenceWithLock 在同一个锁内插入用户消息，防止序号竞态
    @Test
    void submitAnswer_getNextSequenceWithLock_insertsUserMessageWithinLock() {
        InterviewEntity interview = createInterview(100L, 1L, InterviewStatusEnum.CREATED);
        when(interviewMapper.selectById(100L)).thenReturn(interview);
        when(messageMapper.selectMaxSeq(100L)).thenReturn(0);

        ArgumentCaptor<InterviewMessageEntity> captor = ArgumentCaptor.forClass(InterviewMessageEntity.class);
        when(messageMapper.insert(captor.capture())).thenReturn(1);
        when(messageMapper.findByInterviewId(anyLong(), anyInt())).thenReturn(List.of());

        InterviewEngine.EngineResult engineResult = new InterviewEngine.EngineResult(
                "AI response", QuestionTypeEnum.SELF_INTRO, false, false, null, null, null, null, null, null, null, null);
        when(interviewEngine.processAnswerStream(any(), any(), any(), any(), any(SseEmitter.class))).thenReturn(engineResult);

        syncRunTaskExecutor();
        interviewService.submitAnswer(1L, 100L, new SubmitAnswerRequest("my answer"));

        // 验证只插入一次用户消息（getNextSequenceWithLock 内部 insert，而非 submitAnswer 中两次）
        List<InterviewMessageEntity> inserts = captor.getAllValues();
        // 第一次 insert 是 getNextSequenceWithLock 中的用户消息，第二次是 processAndStream 中的 AI 消息
        assertEquals(MessageRoleEnum.USER, inserts.getFirst().getRole());
        assertEquals("my answer", inserts.getFirst().getContent());
        assertEquals(1, inserts.getFirst().getSequenceNum());
    }

    // 测：processAndStream 失败时保留用户消息，不清理（P3-14 修复后保留）
    @Test
    void submitAnswer_aiFailure_preservesUserMessage() {
        InterviewEntity interview = createInterview(100L, 1L, InterviewStatusEnum.IN_PROGRESS);
        when(interviewMapper.selectById(100L)).thenReturn(interview);
        when(messageMapper.selectMaxSeq(100L)).thenReturn(1);

        ArgumentCaptor<InterviewMessageEntity> userMsgCaptor = ArgumentCaptor.forClass(InterviewMessageEntity.class);
        when(messageMapper.insert(userMsgCaptor.capture())).thenAnswer(invocation -> {
            InterviewMessageEntity msg = invocation.getArgument(0);
            msg.setId(100L);
            return 1;
        });
        when(messageMapper.findByInterviewId(anyLong(), anyInt())).thenReturn(List.of());

        SseEmitter emitter = new SseEmitter();
        when(interviewEngine.processAnswerStream(any(), any(), any(), any(), any(SseEmitter.class)))
                .thenThrow(new RuntimeException("LLM error"));

        syncRunTaskExecutor();
        assertDoesNotThrow(() ->
                interviewService.submitAnswer(1L, 100L, new SubmitAnswerRequest("my answer")));

        // 用户消息应被保留，不硬删除
        verify(messageMapper, never()).hardDeleteById(anyLong());
        // 消息应已插入
        assertEquals(1, userMsgCaptor.getAllValues().size());
        assertEquals(MessageRoleEnum.USER, userMsgCaptor.getValue().getRole());
        assertEquals("my answer", userMsgCaptor.getValue().getContent());
    }

    // ========== P3-15: 活跃面试 Redis 分布式锁 ==========

    // 测：并发 startInterview 时只有一个能获取锁（第二个被拒绝）
    @Test
    void startInterview_concurrentRequests_onlyOneSucceeds() {
        when(quotaClient.consume(eq("INTERVIEW")))
                .thenReturn(Result.success(new QuotaCheckResult(true, "FREE", null, null)));
        when(interviewMapper.selectCount(any())).thenReturn(0L);

        org.mockito.Mockito.lenient().when(stringRedisTemplate.opsForValue())
                .thenReturn(mock(org.springframework.data.redis.core.ValueOperations.class));
        org.mockito.Mockito.lenient().when(stringRedisTemplate.opsForValue()
                        .setIfAbsent(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
                                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(true, false);
        org.mockito.Mockito.lenient().when(stringRedisTemplate.delete(anyString())).thenReturn(true);

        doAnswer(inv -> {
            InterviewEntity e = inv.getArgument(0);
            e.setId(100L);
            return 1;
        }).when(interviewMapper).insert(any(InterviewEntity.class));

        InterviewMessageEntity firstMsg = new InterviewMessageEntity();
        firstMsg.setId(200L);
        firstMsg.setInterviewId(100L);
        firstMsg.setRole(MessageRoleEnum.AI);
        firstMsg.setContent("请做自我介绍");
        firstMsg.setQuestionType(QuestionTypeEnum.SELF_INTRO);
        firstMsg.setSequenceNum(1);
        when(interviewEngine.generateFirstQuestion(any(), any())).thenReturn(firstMsg);
        when(messageMapper.insert(any(InterviewMessageEntity.class))).thenReturn(1);

        StartInterviewRequest request = new StartInterviewRequest(CompanyEnum.ALIBABA, PositionEnum.BACKEND, null);

        // 第一个请求成功
        assertDoesNotThrow(() -> interviewService.startInterview(1L, request));

        // 第二个请求因锁失败
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> interviewService.startInterview(2L, request));
        assertTrue(ex.getMessage().contains("进行中的面试"));
    }

    // ========== helpers ==========
    private void syncRunTaskExecutor() {
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(taskExecutor).execute(any(Runnable.class));
    }

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

    private ResumeVO resumeVOWithSections() {
        SectionVO education = new SectionVO(1L, 100L, "education", "教育经历", 0, true,
                "{\"items\":[{\"field\":\"软件工程\",\"degree\":\"本科\",\"institution\":\"福建师范大学\",\"highlights\":[\"一等奖学金\"]}]}",
                LocalDateTime.now(), LocalDateTime.now());
        SectionVO project = new SectionVO(2L, 100L, "project", "项目经历", 1, true,
                "{\"items\":[{\"name\":\"蜂购\",\"technologies\":[\"Spring\",\"Redis\"],\"highlights\":[\"QPS优化\"]}]}",
                LocalDateTime.now(), LocalDateTime.now());
        SectionVO skills = new SectionVO(3L, 100L, "skills", "专业技能", 2, true,
                "{\"categories\":[{\"name\":\"Java\",\"skills\":[\"熟悉多线程\"]}]}",
                LocalDateTime.now(), LocalDateTime.now());
        SectionVO summary = new SectionVO(4L, 100L, "summary", "个人概述", 3, true,
                "{\"text\":\"学习能力强\"}",
                LocalDateTime.now(), LocalDateTime.now());
        return new ResumeVO(100L, 1L, "resume.pdf", null, null, null, true, "我的简历", null, null, null, true, null, 0,
                List.of(education, project, skills, summary), LocalDateTime.now(), LocalDateTime.now());
    }
}
