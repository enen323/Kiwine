package com.prepdeck.interview.service;

import com.prepdeck.common.dto.QuotaCheckResult;
import com.prepdeck.common.exception.BadRequestException;
import com.prepdeck.interview.client.QuotaClient;
import com.prepdeck.interview.client.ResumeClient;
import com.prepdeck.interview.domain.InterviewEntity;
import com.prepdeck.interview.domain.InterviewMessageEntity;
import com.prepdeck.interview.dto.request.StartInterviewRequest;
import com.prepdeck.interview.dto.request.SubmitAnswerRequest;
import com.prepdeck.interview.dto.response.StartInterviewResponse;
import com.prepdeck.interview.enums.CompanyEnum;
import com.prepdeck.interview.enums.InterviewStageEnum;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
// 集成-2: 面试完整流程 — startInterview → submitAnswer × N → completeInterview
// 验证全链路状态流转、消息序号、阶段流转的端到端一致性
class InterviewFullFlowTest {

    @Mock private InterviewMapper interviewMapper;
    @Mock private InterviewMessageMapper messageMapper;
    @Mock private InterviewQuestionScoreMapper questionScoreMapper;
    @Mock private InterviewEngine interviewEngine;
    @Mock private ResumeClient resumeClient;
    @Mock private QuotaClient quotaClient;
    @Mock private java.util.concurrent.Executor taskExecutor;
    @Mock private java.util.concurrent.ScheduledExecutorService interviewScheduler;
    @Mock private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    private InterviewServiceImpl interviewService;
    private final List<InterviewMessageEntity> messageDb = new ArrayList<>(); // 模拟消息表
    private InterviewEntity capturedInterview; // 捕获面试实体，追踪状态变化

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
        messageDb.clear();
        capturedInterview = null;
    }

    // ========== 完整流程：start → 3次回答 → complete ==========

    // 测：模拟完整面试流程 — 开始→追问→阶段流转→完成，验证状态、序号、评分的全链路一致性
    @Test
    void fullFlow_startToCompletion_stateAndMessagesConsistent() {
        // ---- Step 1: startInterview ----
        stubStartInterview();
        StartInterviewResponse startResp = interviewService.startInterview(1L,
                new StartInterviewRequest(CompanyEnum.ALIBABA, PositionEnum.BACKEND, null));

        Long interviewId = startResp.interview().id();
        assertEquals(InterviewStatusEnum.CREATED, startResp.interview().status());
        assertEquals("请做自我介绍", startResp.firstQuestion().content());
        assertEquals(1, startResp.firstQuestion().sequenceNum()); // 首问 seq=1

        // 验证首条消息已入"库"
        assertEquals(1, messageDb.size());
        assertEquals(MessageRoleEnum.AI, messageDb.get(0).getRole());

        // ---- Step 2: 第一次回答（CREATED → IN_PROGRESS） ----
        stubSubmitAnswer(interviewId, 1, 2); // 1条历史AI消息
        interviewService.submitAnswer(1L, interviewId, new SubmitAnswerRequest("我是Java工程师"));

        // 验证：用户回答 seq=2，AI回复 seq=3，状态 IN_PROGRESS
        assertEquals(3, messageDb.size());
        assertEquals(MessageRoleEnum.USER, messageDb.get(1).getRole());
        assertEquals("我是Java工程师", messageDb.get(1).getContent());
        assertEquals(2, messageDb.get(1).getSequenceNum());
        assertEquals(MessageRoleEnum.AI, messageDb.get(2).getRole());
        assertEquals(3, messageDb.get(2).getSequenceNum());
        assertEquals(InterviewStatusEnum.IN_PROGRESS, capturedInterview.getStatus());
        assertNotNull(capturedInterview.getStartedAt());
        assertEquals(1, capturedInterview.getTotalQuestions());

        // ---- Step 3: 第二次回答（阶段内追问） ----
        stubSubmitAnswer(interviewId, 3, 4); // 3条历史
        interviewService.submitAnswer(1L, interviewId, new SubmitAnswerRequest("我负责订单系统"));

        // 验证：seq=4,5；状态仍 IN_PROGRESS；totalQuestions=2
        assertEquals(5, messageDb.size());
        assertEquals(4, messageDb.get(3).getSequenceNum());
        assertEquals(5, messageDb.get(4).getSequenceNum());
        assertEquals(InterviewStatusEnum.IN_PROGRESS, capturedInterview.getStatus());
        assertEquals(2, capturedInterview.getTotalQuestions());

        // ---- Step 4: 第三次回答（阶段完成，流转到下一阶段） ----
        stubSubmitAnswer_stageComplete(interviewId, 5); // 5条历史
        interviewService.submitAnswer(1L, interviewId, new SubmitAnswerRequest("我优化了支付链路"));

        // 验证：阶段从 SELF_INTRO → PROJECT_DEEP_DIVE，stageOrder=1
        assertEquals(7, messageDb.size());
        assertEquals(InterviewStageEnum.PROJECT_DEEP_DIVE, capturedInterview.getCurrentStage());
        assertEquals(1, capturedInterview.getStageOrder());
        assertEquals(3, capturedInterview.getTotalQuestions());

        // ---- Step 5: 第四次回答（PROROJECT_DEEP_DIVE 阶段内） ----
        stubSubmitAnswer(interviewId, 7, 8); // 7条历史
        interviewService.submitAnswer(1L, interviewId, new SubmitAnswerRequest("使用Spring Cloud Alibaba"));

        assertEquals(9, messageDb.size());
        assertEquals(8, messageDb.get(7).getSequenceNum()); // 用户回答 seq=8
        assertEquals(9, messageDb.get(8).getSequenceNum()); // AI回复 seq=9

        // ---- Step 6: 第五次回答（全部阶段完成 → COMPLETED） ----
        stubSubmitAnswer_complete(interviewId, 9); // 9条历史
        interviewService.submitAnswer(1L, interviewId, new SubmitAnswerRequest("我提升了系统可用性"));

        // 验证：状态 COMPLETED，但评分由异步聚合延迟生成，此时仍为 null
        assertEquals(InterviewStatusEnum.COMPLETED, capturedInterview.getStatus());
        assertEquals(InterviewStageEnum.COMPLETED, capturedInterview.getCurrentStage());
        assertNull(capturedInterview.getScoreTotal()); // async aggregation not yet run
        assertNull(capturedInterview.getScoreTech());
        assertNull(capturedInterview.getScoreExpression());
        assertNotNull(capturedInterview.getCompletedAt());
        assertEquals(11, messageDb.size()); // 首问1 + 5轮×2(user+AI)
    }

    // ========== 完整流程：start → 3次回答 → terminate（未完成即终止） ==========

    // 测：模拟面试中途终止流程 — 开始→追问→terminate，验证状态、时长计算
    @Test
    void fullFlow_startToTerminate_verifiesTerminatedState() {
        // ---- Step 1: startInterview ----
        stubStartInterview();
        StartInterviewResponse startResp = interviewService.startInterview(1L,
                new StartInterviewRequest(CompanyEnum.ALIBABA, PositionEnum.BACKEND, null));
        Long interviewId = startResp.interview().id();

        // ---- Step 2: 第一次回答（CREATED → IN_PROGRESS） ----
        stubSubmitAnswer(interviewId, 1, 2);
        interviewService.submitAnswer(1L, interviewId, new SubmitAnswerRequest("我是Java工程师"));
        assertEquals(InterviewStatusEnum.IN_PROGRESS, capturedInterview.getStatus());
        assertNotNull(capturedInterview.getStartedAt());

        // ---- Step 3: 终止面试 ----
        capturedInterview.setStartedAt(LocalDateTime.now().minusMinutes(10));
        when(interviewMapper.selectById(interviewId)).thenAnswer(inv -> capturedInterview);

        interviewService.terminateInterview(1L, interviewId);

        // 验证：状态 TERMINATED，completedAt 已设置
        assertEquals(InterviewStatusEnum.TERMINATED, capturedInterview.getStatus());
        assertNotNull(capturedInterview.getCompletedAt());
        assertTrue(capturedInterview.getDurationSeconds() >= 600);
        verify(interviewMapper, atLeastOnce()).updateById(capturedInterview);
    }

    // ========== 完整流程：start → complete → soft delete ==========

    // 测：COMPLETED 面试软删除 — interview.deleted=true
    @Test
    void fullFlow_completedThenSoftDelete_marksDeleted() {
        stubStartInterview();
        StartInterviewResponse startResp = interviewService.startInterview(1L,
                new StartInterviewRequest(CompanyEnum.ALIBABA, PositionEnum.BACKEND, null));
        Long interviewId = startResp.interview().id();

        // 直接 complete（模拟所有阶段完成）
        stubSubmitAnswer_complete(interviewId, 1);
        interviewService.submitAnswer(1L, interviewId, new SubmitAnswerRequest("面试结束"));

        assertEquals(InterviewStatusEnum.COMPLETED, capturedInterview.getStatus());

        // soft delete
        when(interviewMapper.selectById(interviewId)).thenAnswer(inv -> capturedInterview);

        interviewService.deleteInterview(1L, interviewId);

        assertTrue(capturedInterview.getDeleted());
        verify(interviewMapper, atLeastOnce()).updateById(capturedInterview);
        verify(messageMapper).update(any(), any());
    }

    // ========== 终止后不可再回答 ==========

    // 测：TERMINATED 面试 submitAnswer → 抛 BadRequestException，零副作用
    @Test
    void fullFlow_afterTerminate_cannotSubmitAnswer() {
        stubStartInterview();
        StartInterviewResponse startResp = interviewService.startInterview(1L,
                new StartInterviewRequest(CompanyEnum.ALIBABA, PositionEnum.BACKEND, null));
        Long interviewId = startResp.interview().id();

        // 提交一次回答
        stubSubmitAnswer(interviewId, 1, 2);
        interviewService.submitAnswer(1L, interviewId, new SubmitAnswerRequest("回答1"));

        // 终止
        capturedInterview.setStartedAt(LocalDateTime.now().minusMinutes(5));
        when(interviewMapper.selectById(interviewId)).thenAnswer(inv -> capturedInterview);
        interviewService.terminateInterview(1L, interviewId);

        // 确保后续 selectById 返回 TERMINATED 状态的面试
        when(interviewMapper.selectById(interviewId)).thenAnswer(inv -> capturedInterview);

        // 再次提交 → 抛 BadRequestException
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> interviewService.submitAnswer(1L, interviewId, new SubmitAnswerRequest("回答2")));
        assertEquals(com.prepdeck.common.enums.ErrorCode.INTERVIEW_ALREADY_COMPLETED.getCode(), ex.getCode());
    }

    // ========== 辅助方法 ==========

    // stub startInterview 的完整流程
    private void stubStartInterview() {
        when(quotaClient.consume(eq("INTERVIEW")))
                .thenReturn(com.prepdeck.common.model.Result.success(QuotaCheckResult.allowed("FREE")));
        when(interviewMapper.selectCount(any())).thenReturn(0L);
        doAnswer(inv -> {
            InterviewEntity e = inv.getArgument(0);
            capturedInterview = e;
            e.setId(500L);
            return 1;
        }).when(interviewMapper).insert(any(InterviewEntity.class));

        InterviewMessageEntity firstMsg = new InterviewMessageEntity();
        firstMsg.setId(501L);
        firstMsg.setInterviewId(500L);
        firstMsg.setRole(MessageRoleEnum.AI);
        firstMsg.setContent("请做自我介绍");
        firstMsg.setQuestionType(QuestionTypeEnum.SELF_INTRO);
        firstMsg.setSequenceNum(1);
        when(interviewEngine.generateFirstQuestion(any(), any())).thenReturn(firstMsg);
        when(messageMapper.insert(any(InterviewMessageEntity.class))).thenAnswer(inv -> {
            InterviewMessageEntity m = inv.getArgument(0);
            m.setId((long) (messageDb.size() + 1000));
            messageDb.add(m);
            return 1;
        });
        when(interviewMapper.selectById(500L)).thenAnswer(inv -> capturedInterview);
    }

    // stub 普通追问（stageCompleted=false, interviewCompleted=false）
    private void stubSubmitAnswer(Long interviewId, int historySize, int nextUserSeq) {
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(taskExecutor).execute(any(Runnable.class));

        when(messageMapper.selectMaxSeq(interviewId)).thenReturn(historySize);
        when(messageMapper.insert(any(InterviewMessageEntity.class))).thenAnswer(inv -> {
            InterviewMessageEntity m = inv.getArgument(0);
            m.setId((long) (messageDb.size() + 1000));
            messageDb.add(m);
            return 1;
        });
        when(messageMapper.findByInterviewId(anyLong(), anyInt())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            return messageDb.stream().filter(m -> m.getInterviewId().equals(id)).toList();
        });

        InterviewEngine.EngineResult result = new InterviewEngine.EngineResult(
                "追问: " + nextUserSeq, QuestionTypeEnum.SELF_INTRO, false, false, null,
                null, null, null, null, null, null, null
        );
        when(interviewEngine.processAnswerStream(any(), any(), any(), any(), any(SseEmitter.class))).thenReturn(result);
    }

    // stub 阶段完成（stageCompleted=true, nextStage=PROJECT_DEEP_DIVE）
    private void stubSubmitAnswer_stageComplete(Long interviewId, int historySize) {
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(taskExecutor).execute(any(Runnable.class));

        when(messageMapper.selectMaxSeq(interviewId)).thenReturn(historySize);
        when(messageMapper.insert(any(InterviewMessageEntity.class))).thenAnswer(inv -> {
            InterviewMessageEntity m = inv.getArgument(0);
            m.setId((long) (messageDb.size() + 1000));
            messageDb.add(m);
            return 1;
        });
        when(messageMapper.findByInterviewId(anyLong(), anyInt())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            return messageDb.stream().filter(m -> m.getInterviewId().equals(id)).toList();
        });

        InterviewEngine.EngineResult result = new InterviewEngine.EngineResult(
                "进入项目深挖阶段", QuestionTypeEnum.TECH_PROJECT, true, false, InterviewStageEnum.PROJECT_DEEP_DIVE,
                null, null, null, null, null, null, null
        );
        when(interviewEngine.processAnswerStream(any(), any(), any(), any(), any(SseEmitter.class))).thenReturn(result);
    }

    // stub 面试完成（interviewCompleted=true, 带评分）
    private void stubSubmitAnswer_complete(Long interviewId, int historySize) {
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(taskExecutor).execute(any(Runnable.class));

        when(messageMapper.selectMaxSeq(interviewId)).thenReturn(historySize);
        when(messageMapper.insert(any(InterviewMessageEntity.class))).thenAnswer(inv -> {
            InterviewMessageEntity m = inv.getArgument(0);
            m.setId((long) (messageDb.size() + 1000));
            messageDb.add(m);
            return 1;
        });
        when(messageMapper.findByInterviewId(anyLong(), anyInt())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            return messageDb.stream().filter(m -> m.getInterviewId().equals(id)).toList();
        });

        InterviewEngine.EngineResult result = new InterviewEngine.EngineResult(
                "面试结束，感谢参与。", null, true, true, InterviewStageEnum.COMPLETED,
                85, 80, 70, 85, 75, 70, "建议：多练习系统设计"
        );
        when(interviewEngine.processAnswerStream(any(), any(), any(), any(), any(SseEmitter.class))).thenReturn(result);
    }
}
