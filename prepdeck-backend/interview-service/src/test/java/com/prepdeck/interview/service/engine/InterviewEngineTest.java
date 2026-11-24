package com.prepdeck.interview.service.engine;

import com.prepdeck.common.ai.AiClient;
import com.prepdeck.common.exception.BadRequestException;
import com.prepdeck.interview.dto.model.ResumeProfile;
import com.prepdeck.interview.domain.InterviewEntity;
import com.prepdeck.interview.domain.InterviewMessageEntity;
import com.prepdeck.interview.enums.*;
import com.prepdeck.interview.repository.InterviewMapper;
import com.prepdeck.interview.engine.PromptBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class InterviewEngineTest {

    @Mock
    private InterviewMapper interviewMapper;

    @Mock
    private AiClient aiClient;

    @Mock
    private PromptBuilder promptBuilder;

    private InterviewEngine engine;

    @BeforeEach
    void setUp() {
        lenient().when(promptBuilder.buildSystemPrompt(any(), any(), any(), any(), any()))
                .thenReturn("system prompt");
        lenient().when(promptBuilder.buildProfileText(any()))
                .thenReturn("简历内容");

        engine = new InterviewEngine(interviewMapper, aiClient, promptBuilder);
    }

    // ========== generateFirstQuestion ==========

    @Test
    void generateFirstQuestion_usesLLM() {
        ResumeProfile profile = new ResumeProfile(
                List.of(new ResumeProfile.EducationItem("软件工程", List.of("一等奖学金"))),
                List.of(new ResumeProfile.ProjectItem("蜂购", List.of("Spring", "Redis"), List.of("QPS优化"))),
                List.of(new ResumeProfile.SkillCategory("Java", List.of("熟悉多线程"))),
                "学习能力强"
        );

        InterviewEntity interview = createInterview(1L, "ALIBABA", "BACKEND", InterviewStageEnum.SELF_INTRO);
        when(aiClient.chat(anyString(), anyString())).thenReturn("请介绍一下你自己");

        InterviewMessageEntity msg = engine.generateFirstQuestion(interview, profile);

        assertEquals("请介绍一下你自己", msg.getContent());
        assertEquals(MessageRoleEnum.AI, msg.getRole());
        assertEquals(QuestionTypeEnum.SELF_INTRO, msg.getQuestionType());
        assertEquals(1, msg.getSequenceNum());
        verify(aiClient).chat(anyString(), eq("面试官：你好，请候选人做自我介绍。"));
    }

    // ========== processAnswer ==========

    @Test
    void processAnswer_stageNotComplete_generatesFollowUp() {
        ResumeProfile profile = createProfile();
        InterviewEntity interview = createInterview(1L, "ALIBABA", "BACKEND", InterviewStageEnum.SELF_INTRO);
        InterviewMessageEntity userMsg = createUserMessage("我的回答", QuestionTypeEnum.SELF_INTRO);
        List<InterviewMessageEntity> history = List.of(createAiMessage(QuestionTypeEnum.SELF_INTRO, 1));

        when(aiClient.chat(anyString(), anyString())).thenReturn("能具体说说吗？");

        InterviewEngine.EngineResult result = engine.processAnswer(interview, profile, userMsg, history);

        assertFalse(result.stageCompleted());
        assertFalse(result.interviewCompleted());
        assertEquals(QuestionTypeEnum.SELF_INTRO, result.questionType());
        assertEquals("能具体说说吗？", result.aiResponse());
    }

    @Test
    void processAnswer_aiDeclaredCompleteBeforeMin_ignoresTokenAndContinues() {
        ResumeProfile profile = createProfile();
        InterviewEntity interview = createInterview(1L, "ALIBABA", "BACKEND", InterviewStageEnum.SELF_INTRO);
        InterviewMessageEntity userMsg = createUserMessage("回答", QuestionTypeEnum.SELF_INTRO);
        List<InterviewMessageEntity> history = List.of(
                createAiMessage(QuestionTypeEnum.SELF_INTRO, 1, "你好，请自我介绍。[ROUND_COMPLETE]"),
                createAiMessage(QuestionTypeEnum.SELF_INTRO, 2, "能具体说说吗？")
        );

        when(aiClient.chat(anyString(), anyString())).thenReturn("追问内容");

        InterviewEngine.EngineResult result = engine.processAnswer(interview, profile, userMsg, history);

        assertFalse(result.stageCompleted());
        assertFalse(result.interviewCompleted());
        assertEquals("追问内容", result.aiResponse());
        verify(aiClient).chat(anyString(), anyString());
    }

    @Test
    void processAnswer_stageComplete_transitionsToNextStage() {
        ResumeProfile profile = createProfile();
        InterviewEntity interview = createInterview(1L, "ALIBABA", "BACKEND", InterviewStageEnum.SELF_INTRO);
        InterviewMessageEntity userMsg = createUserMessage("回答", QuestionTypeEnum.SELF_INTRO);
        List<InterviewMessageEntity> history = List.of(
                createAiMessage(QuestionTypeEnum.SELF_INTRO, 1),
                createAiMessage(QuestionTypeEnum.SELF_INTRO, 2),
                createAiMessage(QuestionTypeEnum.SELF_INTRO, 3, "[ROUND_COMPLETE]")
        );

        when(aiClient.chat(anyString(), anyString())).thenReturn("接下来我们进入项目深挖阶段。请详细介绍你最得意的项目。");

        InterviewEngine.EngineResult result = engine.processAnswer(interview, profile, userMsg, history);

        assertTrue(result.stageCompleted());
        assertFalse(result.interviewCompleted());
        assertEquals(InterviewStageEnum.PROJECT_DEEP_DIVE, result.nextStage());
        assertEquals(QuestionTypeEnum.TECH_PROJECT, result.questionType());
        verify(aiClient).chat(anyString(), anyString());
    }

    @Test
    void processAnswer_stageComplete_nextStageIsCompleted_returnsNoScore() {
        ResumeProfile profile = createProfile();
        InterviewEntity interview = createInterview(1L, "ALIBABA", "BACKEND", InterviewStageEnum.REVERSE_QA);
        InterviewMessageEntity userMsg = createUserMessage("我的问题", QuestionTypeEnum.REVERSE_QA);
        List<InterviewMessageEntity> history = List.of(
                createAiMessage(QuestionTypeEnum.REVERSE_QA, 1),
                createAiMessage(QuestionTypeEnum.REVERSE_QA, 2),
                createAiMessage(QuestionTypeEnum.REVERSE_QA, 3)
        );

        InterviewEngine.EngineResult result = engine.processAnswer(interview, profile, userMsg, history);

        assertNotNull(result);
        assertTrue(result.interviewCompleted());
        assertEquals(InterviewStageEnum.COMPLETED, result.nextStage());
        assertNull(result.scoreTotal());
    }

    @Test
    void processAnswer_twoAiMessagesNotComplete() {
        ResumeProfile profile = createProfile();
        InterviewEntity interview = createInterview(1L, "ALIBABA", "BACKEND", InterviewStageEnum.TECH_ASSESSMENT);
        InterviewMessageEntity userMsg = createUserMessage("回答", QuestionTypeEnum.TECH_BASIC);
        List<InterviewMessageEntity> history = List.of(
                createAiMessage(QuestionTypeEnum.TECH_BASIC, 1),
                createAiMessage(QuestionTypeEnum.TECH_BASIC, 2)
        );

        when(aiClient.chat(anyString(), anyString())).thenReturn("追问");

        InterviewEngine.EngineResult result = engine.processAnswer(interview, profile, userMsg, history);

        assertFalse(result.stageCompleted());
        assertFalse(result.interviewCompleted());
        assertEquals("追问", result.aiResponse());
    }

    // ========== completeInterview ==========

    @Test
    void completeInterview_parsesJsonScores() {
        ResumeProfile profile = createProfile();
        List<InterviewMessageEntity> history = List.of(
                createUserMessage("回答1", QuestionTypeEnum.SELF_INTRO),
                createAiMessage(QuestionTypeEnum.SELF_INTRO, 1),
                createAiMessage(QuestionTypeEnum.SELF_INTRO, 2),
                createAiMessage(QuestionTypeEnum.SELF_INTRO, 3)
        );

        when(aiClient.chat(anyString(), anyString(), eq("json_object")))
                .thenReturn("{\"scoreTech\":80,\"scoreExpression\":70,\"scoreProject\":75,\"scoreLogic\":80,\"scoreAdaptability\":85,\"suggestion\":\"继续努力\",\"closingRemark\":\"面试结束\"}");

        InterviewEngine.EngineResult result = engine.completeInterview(profile, history, CompanyEnum.ALIBABA, PositionEnum.BACKEND);

        assertNotNull(result);
        assertEquals(InterviewStageEnum.COMPLETED, result.nextStage());
    }

    @Test
    void completeInterview_jsonParseFailure_returnsFallback() {
        ResumeProfile profile = createProfile();
        List<InterviewMessageEntity> history = List.of(
                createUserMessage("回答", QuestionTypeEnum.SELF_INTRO),
                createAiMessage(QuestionTypeEnum.SELF_INTRO, 1)
        );

        when(aiClient.chat(anyString(), anyString(), eq("json_object")))
                .thenReturn("invalid json {{[");

        InterviewEngine.EngineResult result = engine.completeInterview(profile, history, CompanyEnum.ALIBABA, PositionEnum.BACKEND);

        assertNotNull(result);
        assertEquals(InterviewStageEnum.COMPLETED, result.nextStage());
        assertEquals(60, result.scoreTotal());
    }

    // ========== safeParseCompany (reflection) ==========

    @Test
    void safeParseCompanyValid() throws Exception {
        Method method = InterviewEngine.class.getDeclaredMethod("safeParseCompany", String.class);
        method.setAccessible(true);

        CompanyEnum result = (CompanyEnum) method.invoke(engine, "TENCENT");
        assertEquals(CompanyEnum.TENCENT, result);
    }

    @Test
    void safeParseCompanyNull() throws Exception {
        Method method = InterviewEngine.class.getDeclaredMethod("safeParseCompany", String.class);
        method.setAccessible(true);

        assertThrows(BadRequestException.class, () -> {
            try {
                method.invoke(engine, (Object) null);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        });
    }

    @Test
    void safeParseCompanyBlank() throws Exception {
        Method method = InterviewEngine.class.getDeclaredMethod("safeParseCompany", String.class);
        method.setAccessible(true);

        assertThrows(BadRequestException.class, () -> {
            try {
                method.invoke(engine, "  ");
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        });
    }

    @Test
    void safeParseCompanyUnknown() throws Exception {
        Method method = InterviewEngine.class.getDeclaredMethod("safeParseCompany", String.class);
        method.setAccessible(true);

        assertThrows(BadRequestException.class, () -> {
            try {
                method.invoke(engine, "UNKNOWN_COMPANY");
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        });
    }

    // ========== safeParsePosition (reflection) ==========

    @Test
    void safeParsePositionValid() throws Exception {
        Method method = InterviewEngine.class.getDeclaredMethod("safeParsePosition", String.class);
        method.setAccessible(true);

        PositionEnum result = (PositionEnum) method.invoke(engine, "FRONTEND");
        assertEquals(PositionEnum.FRONTEND, result);
    }

    @Test
    void safeParsePositionNull() throws Exception {
        Method method = InterviewEngine.class.getDeclaredMethod("safeParsePosition", String.class);
        method.setAccessible(true);

        assertThrows(BadRequestException.class, () -> {
            try {
                method.invoke(engine, (Object) null);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        });
    }

    @Test
    void safeParsePositionUnknown() throws Exception {
        Method method = InterviewEngine.class.getDeclaredMethod("safeParsePosition", String.class);
        method.setAccessible(true);

        assertThrows(BadRequestException.class, () -> {
            try {
                method.invoke(engine, "UNKNOWN_POS");
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        });
    }

    // ========== detectRoundComplete ==========

    @Test
    void detectRoundComplete_returnsTrueWhenTokenAtEnd() throws Exception {
        Method method = InterviewEngine.class.getDeclaredMethod("detectRoundComplete", String.class);
        method.setAccessible(true);

        assertTrue((Boolean) method.invoke(engine, "面试结束[ROUND_COMPLETE]"));
        assertTrue((Boolean) method.invoke(engine, "评价内容\n[ROUND_COMPLETE]"));
    }

    @Test
    void detectRoundComplete_returnsFalseWhenTokenMissing() throws Exception {
        Method method = InterviewEngine.class.getDeclaredMethod("detectRoundComplete", String.class);
        method.setAccessible(true);

        assertFalse((Boolean) method.invoke(engine, "面试结束"));
        assertFalse((Boolean) method.invoke(engine, ""));
        assertFalse((Boolean) method.invoke(engine, (Object) null));
    }

    // ========== buildControlSystemPrompt ==========

    @Test
    void buildControlSystemPrompt_skipReturnsSkipInstruction() throws Exception {
        Method method = InterviewEngine.class.getDeclaredMethod("buildControlSystemPrompt", String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(engine, "SKIP");
        assertTrue(result.contains("跳过"));
        assertTrue(result.contains("不要提及这是切换"));
    }

    @Test
    void buildControlSystemPrompt_hintReturnsHintInstruction() throws Exception {
        Method method = InterviewEngine.class.getDeclaredMethod("buildControlSystemPrompt", String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(engine, "HINT");
        assertTrue(result.contains("提示"));
        assertTrue(result.contains("不要直接给出答案"));
    }

    @Test
    void buildControlSystemPrompt_endReturnsEndInstruction() throws Exception {
        Method method = InterviewEngine.class.getDeclaredMethod("buildControlSystemPrompt", String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(engine, "END");
        assertTrue(result.contains("结束"));
        assertFalse(result.contains("[ROUND_COMPLETE]"));
    }

    // ========== processAnswerStream min round guard ==========

    @Test
    void processAnswerStream_aiDeclaredCompleteBeforeMin_ignoresTokenAndContinues() {
        ResumeProfile profile = createProfile();
        InterviewEntity interview = createInterview(1L, "ALIBABA", "BACKEND", InterviewStageEnum.SELF_INTRO);
        InterviewMessageEntity userMsg = createUserMessage("回答", QuestionTypeEnum.SELF_INTRO);
        List<InterviewMessageEntity> history = List.of(
                createAiMessage(QuestionTypeEnum.SELF_INTRO, 1, "你好，请自我介绍。[ROUND_COMPLETE]"),
                createAiMessage(QuestionTypeEnum.SELF_INTRO, 2)
        );

        when(aiClient.chatStream(anyString(), anyList(), any())).thenAnswer(invocation -> {
            SseEmitter emitter = invocation.getArgument(2);
            emitter.send(SseEmitter.event().name("chunk").data("追问内容"));
            return CompletableFuture.completedFuture("追问内容");
        });

        InterviewEngine.EngineResult result = engine.processAnswerStream(interview, profile, userMsg, history, new SseEmitter());

        assertFalse(result.stageCompleted());
        assertFalse(result.interviewCompleted());
        assertEquals("追问内容", result.aiResponse());
    }

    // ========== processAnswerStream max questions ==========

    @Test
    void processAnswerStream_maxQuestionsReached_forcesEnd() {
        ResumeProfile profile = createProfile();
        InterviewEntity interview = createInterview(1L, "ALIBABA", "BACKEND", InterviewStageEnum.SELF_INTRO);
        InterviewMessageEntity userMsg = createUserMessage("回答", QuestionTypeEnum.SELF_INTRO);
        List<InterviewMessageEntity> history = List.of(
                createAiMessage(QuestionTypeEnum.SELF_INTRO, 1),
                createAiMessage(QuestionTypeEnum.SELF_INTRO, 2),
                createAiMessage(QuestionTypeEnum.SELF_INTRO, 3),
                createAiMessage(QuestionTypeEnum.SELF_INTRO, 4),
                createAiMessage(QuestionTypeEnum.SELF_INTRO, 5),
                createAiMessage(QuestionTypeEnum.SELF_INTRO, 6),
                createAiMessage(QuestionTypeEnum.SELF_INTRO, 7),
                createAiMessage(QuestionTypeEnum.SELF_INTRO, 8),
                createAiMessage(QuestionTypeEnum.SELF_INTRO, 9),
                createAiMessage(QuestionTypeEnum.SELF_INTRO, 10)
        );

        when(aiClient.chatStream(anyString(), anyList(), any())).thenAnswer(invocation -> {
            SseEmitter emitter = invocation.getArgument(2);
            emitter.send(SseEmitter.event().name("chunk").data("面试结束[ROUND_COMPLETE]"));
            return CompletableFuture.completedFuture("面试结束[ROUND_COMPLETE]");
        });

        InterviewEngine.EngineResult result = engine.processAnswerStream(interview, profile, userMsg, history, new SseEmitter());

        assertTrue(result.interviewCompleted());
        assertTrue(result.aiResponse().contains("[ROUND_COMPLETE]"));
    }

    @Test
    void processAnswerStream_reverseQaForceEnd_usesClosingRemarkNotQuestion() {
        ResumeProfile profile = createProfile();
        InterviewEntity interview = createInterview(1L, "ALIBABA", "BACKEND", InterviewStageEnum.REVERSE_QA);
        InterviewMessageEntity userMsg = createUserMessage("我的问题", QuestionTypeEnum.REVERSE_QA);
        List<InterviewMessageEntity> history = List.of(
                createAiMessage(QuestionTypeEnum.REVERSE_QA, 1),
                createAiMessage(QuestionTypeEnum.REVERSE_QA, 2),
                createAiMessage(QuestionTypeEnum.REVERSE_QA, 3)
        );

        when(aiClient.chatStream(anyString(), anyList(), any())).thenAnswer(invocation -> {
            SseEmitter emitter = invocation.getArgument(2);
            emitter.send(SseEmitter.event().name("chunk").data("感谢你的提问，祝你面试顺利。"));
            return CompletableFuture.completedFuture("感谢你的提问，祝你面试顺利。");
        });

        InterviewEngine.EngineResult result = engine.processAnswerStream(interview, profile, userMsg, history, new SseEmitter());

        assertTrue(result.interviewCompleted());
        assertEquals(InterviewStageEnum.COMPLETED, result.nextStage());
        assertFalse(result.aiResponse().contains("？"));
        assertFalse(result.aiResponse().contains("?"));
    }

    @Test
    void processAnswerStream_reverseQaForceEnd_fallbackReturnsClosingRemarkWhenAiFails() {
        ResumeProfile profile = createProfile();
        InterviewEntity interview = createInterview(1L, "ALIBABA", "BACKEND", InterviewStageEnum.REVERSE_QA);
        InterviewMessageEntity userMsg = createUserMessage("我的问题", QuestionTypeEnum.REVERSE_QA);
        List<InterviewMessageEntity> history = List.of(
                createAiMessage(QuestionTypeEnum.REVERSE_QA, 1),
                createAiMessage(QuestionTypeEnum.REVERSE_QA, 2),
                createAiMessage(QuestionTypeEnum.REVERSE_QA, 3)
        );

        when(aiClient.chatStream(anyString(), anyList(), any())).thenThrow(new RuntimeException("LLM error"));

        InterviewEngine.EngineResult result = engine.processAnswerStream(interview, profile, userMsg, history, new SseEmitter());

        assertTrue(result.interviewCompleted());
        assertEquals(InterviewStageEnum.COMPLETED, result.nextStage());
        assertEquals("感谢你的提问，祝你面试顺利，我们后续再联系。", result.aiResponse());
    }

    // ========== processControlCommand ==========

    @Test
    void processControlCommand_hintGivesHintResponse() {
        ResumeProfile profile = createProfile();
        InterviewEntity interview = createInterview(1L, "ALIBABA", "BACKEND", InterviewStageEnum.SELF_INTRO);
        List<InterviewMessageEntity> history = List.of(
                createAiMessage(QuestionTypeEnum.SELF_INTRO, 1)
        );

        when(aiClient.chatStream(anyString(), anyList(), any())).thenAnswer(invocation -> {
            SseEmitter emitter = invocation.getArgument(2);
            emitter.send(SseEmitter.event().name("chunk").data("提示内容"));
            return CompletableFuture.completedFuture("提示内容");
        });

        InterviewEngine.EngineResult result = engine.processControlCommand(
                interview, profile, "HINT", history, new SseEmitter());

        assertFalse(result.interviewCompleted());
        assertFalse(result.stageCompleted());
        assertEquals("提示内容", result.aiResponse());
    }

    // ========== scoreQuestion ==========

    @Test
    void scoreQuestion_parsesJsonResponse() {
        when(aiClient.chat(anyString(), anyString(), eq("json_object")))
                .thenReturn("{\"questionDescription\":\"考察分布式事务\",\"score\":72,\"grade\":\"GOOD\",\"feedback\":\"提到幂等键\"}");

        InterviewEngine.QuestionScore score = engine.scoreQuestion("什么是幂等性？", QuestionTypeEnum.TECH_BASIC, "我的回答");

        assertEquals("考察分布式事务", score.questionDescription());
        assertEquals(72, score.score());
        assertEquals("GOOD", score.grade());
        assertEquals("提到幂等键", score.feedback());
        verify(aiClient).chat(anyString(), anyString(), eq("json_object"));
    }

    @Test
    void scoreQuestion_invalidJson_returnsFallback() {
        when(aiClient.chat(anyString(), anyString(), eq("json_object"))).thenReturn("not json");

        InterviewEngine.QuestionScore score = engine.scoreQuestion("问题", QuestionTypeEnum.ALGORITHM, "我的回答");

        assertEquals("", score.questionDescription());
        assertEquals(60, score.score());
        assertNull(score.grade());
        assertEquals("评分解析失败", score.feedback());
    }

    @Test
    void scoreQuestion_nullQuestionType_usesDefault() {
        when(aiClient.chat(anyString(), anyString(), eq("json_object")))
                .thenReturn("{\"questionDescription\":\"综合题\",\"score\":80,\"grade\":\"EXCELLENT\",\"feedback\":\"不错\"}");

        InterviewEngine.QuestionScore score = engine.scoreQuestion("问题", null, "我的回答");

        verify(aiClient).chat(anyString(), contains("综合"), eq("json_object"));
        assertEquals("综合题", score.questionDescription());
        assertEquals(80, score.score());
        assertEquals("EXCELLENT", score.grade());
        assertEquals("不错", score.feedback());
    }

    @Test
    void scoreQuestion_mismatch_triggersRetryAndReturnsConsistent() {
        when(aiClient.chat(anyString(), anyString(), eq("json_object")))
                .thenReturn("{\"questionDescription\":\"题\",\"score\":50,\"grade\":\"GOOD\",\"feedback\":\"一般\"}")
                .thenReturn("{\"questionDescription\":\"题\",\"score\":45,\"grade\":\"POOR\",\"feedback\":\"较差\"}");

        InterviewEngine.QuestionScore score = engine.scoreQuestion("问题", QuestionTypeEnum.TECH_BASIC, "我的回答");

        assertEquals(45, score.score());
        assertEquals("POOR", score.grade());
        assertEquals("较差", score.feedback());
        verify(aiClient, times(2)).chat(anyString(), anyString(), eq("json_object"));
    }

    @Test
    void scoreQuestion_retryStillMismatch_correctsScore() {
        when(aiClient.chat(anyString(), anyString(), eq("json_object")))
                .thenReturn("{\"questionDescription\":\"题\",\"score\":50,\"grade\":\"GOOD\",\"feedback\":\"一般\"}")
                .thenReturn("{\"questionDescription\":\"题\",\"score\":55,\"grade\":\"GOOD\",\"feedback\":\"一般\"}");

        InterviewEngine.QuestionScore score = engine.scoreQuestion("问题", QuestionTypeEnum.TECH_BASIC, "我的回答");

        // GOOD 对应 [70,79]，55 被修正到 70
        assertEquals(70, score.score());
        assertEquals("GOOD", score.grade());
        verify(aiClient, times(2)).chat(anyString(), anyString(), eq("json_object"));
    }

    @Test
    void scoreQuestion_includesCandidateAnswerInPrompt() {
        when(aiClient.chat(anyString(), anyString(), eq("json_object")))
                .thenReturn("{\"questionDescription\":\"题\",\"score\":80,\"grade\":\"EXCELLENT\",\"feedback\":\"不错\"}");

        engine.scoreQuestion("什么是幂等性？", QuestionTypeEnum.TECH_BASIC, "我的回答");

        verify(aiClient).chat(anyString(), contains("候选人回答：我的回答"), eq("json_object"));
    }

    @Test
    void scoreQuestion_nullCandidateAnswerUsesPlaceholder() {
        when(aiClient.chat(anyString(), anyString(), eq("json_object")))
                .thenReturn("{\"questionDescription\":\"题\",\"score\":60,\"grade\":\"AVERAGE\",\"feedback\":\"一般\"}");

        engine.scoreQuestion("问题", QuestionTypeEnum.TECH_BASIC, null);

        verify(aiClient).chat(anyString(), contains("候选人回答：（未提供）"), eq("json_object"));
    }

    // ========== helpers ==========

    private InterviewEntity createInterview(Long id, String company, String position, InterviewStageEnum stage) {
        InterviewEntity interview = new InterviewEntity();
        interview.setId(id);
        interview.setUserId(1L);
        interview.setCompany(company);
        interview.setPosition(position);
        interview.setCurrentStage(stage);
        interview.setStatus(InterviewStatusEnum.IN_PROGRESS);
        return interview;
    }

    private InterviewMessageEntity createUserMessage(String content, QuestionTypeEnum questionType) {
        InterviewMessageEntity msg = new InterviewMessageEntity();
        msg.setRole(MessageRoleEnum.USER);
        msg.setContent(content);
        msg.setQuestionType(questionType);
        return msg;
    }

    private InterviewMessageEntity createAiMessage(QuestionTypeEnum questionType, int sequenceNum) {
        return createAiMessage(questionType, sequenceNum, "AI question text");
    }

    private InterviewMessageEntity createAiMessage(QuestionTypeEnum questionType, int sequenceNum, String content) {
        InterviewMessageEntity msg = new InterviewMessageEntity();
        msg.setRole(MessageRoleEnum.AI);
        msg.setContent(content);
        msg.setQuestionType(questionType);
        msg.setSequenceNum(sequenceNum);
        return msg;
    }

    private ResumeProfile createProfile() {
        return new ResumeProfile(
                List.of(new ResumeProfile.EducationItem("软件工程", List.of("一等奖学金"))),
                List.of(new ResumeProfile.ProjectItem("蜂购", List.of("Spring", "Redis"), List.of("QPS优化"))),
                List.of(new ResumeProfile.SkillCategory("Java", List.of("熟悉多线程"))),
                "学习能力强"
        );
    }
}
