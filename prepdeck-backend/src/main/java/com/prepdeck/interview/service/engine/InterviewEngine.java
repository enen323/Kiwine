package com.prepdeck.interview.service.engine;

import com.prepdeck.common.ai.AiClient;
import com.prepdeck.common.exception.BadRequestException;
import com.prepdeck.interview.dto.model.ResumeProfile;
import com.prepdeck.interview.domain.InterviewEntity;
import com.prepdeck.interview.domain.InterviewMessageEntity;
import com.prepdeck.interview.enums.*;
import com.prepdeck.interview.repository.InterviewMapper;
import com.prepdeck.interview.engine.PromptBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class InterviewEngine {

    private final InterviewMapper interviewMapper;
    private final AiClient aiClient;
    private final PromptBuilder promptBuilder;

    private static final int MIN_EXCHANGES_PER_STAGE = 3;
    private static final int MAX_REVERSE_QA_QUESTIONS = 3;
    private static final Map<InterviewStageEnum, Integer> MAX_QUESTIONS_PER_STAGE = Map.of(
            InterviewStageEnum.SELF_INTRO, 3,
            InterviewStageEnum.PROJECT_DEEP_DIVE, 8,
            InterviewStageEnum.TECH_ASSESSMENT, 7,
            InterviewStageEnum.REVERSE_QA, MAX_REVERSE_QA_QUESTIONS
    );
    private static final String ROUND_COMPLETE_TOKEN = "[ROUND_COMPLETE]";

    /**
     * 匹配 AI 可能输出的"本轮/面试结束"特殊标记（含 [ROUND_COMPLETE]、[ROUND COMPLETED]、[ROUND-COMPLETE] 等变体），
     * 用于兜底清理，避免该标记泄露到用户可见的回复中。
     */
    private static final Pattern ROUND_COMPLETE_PATTERN =
            Pattern.compile("(?i)\\[?\\s*ROUND[\\s_\\-]*COMPLETE[D]?\\s*\\]?");

    private static final Map<InterviewStageEnum, QuestionTypeEnum> STAGE_QUESTION_TYPE = Map.of(
            InterviewStageEnum.SELF_INTRO, QuestionTypeEnum.SELF_INTRO,
            InterviewStageEnum.PROJECT_DEEP_DIVE, QuestionTypeEnum.TECH_PROJECT,
            InterviewStageEnum.TECH_ASSESSMENT, QuestionTypeEnum.TECH_BASIC,
            InterviewStageEnum.REVERSE_QA, QuestionTypeEnum.REVERSE_QA
    );

    public QuestionTypeEnum getQuestionTypeForStage(InterviewStageEnum stage) {
        return STAGE_QUESTION_TYPE.get(stage);
    }

    private static final Map<InterviewStageEnum, String[]> FALLBACK_QUESTIONS = Map.of(
            InterviewStageEnum.SELF_INTRO, new String[]{"请简单介绍一下你自己和你的技术背景。"},
            InterviewStageEnum.PROJECT_DEEP_DIVE, new String[]{"请介绍一个你最自豪的项目，你在其中扮演什么角色？"},
            InterviewStageEnum.TECH_ASSESSMENT, new String[]{"请谈谈你对微服务架构的理解，以及你用过哪些相关技术？"},
            InterviewStageEnum.REVERSE_QA, new String[]{"你对我们公司有什么想问的吗？"}
    );

    private int getMaxQuestionsForStage(InterviewStageEnum stage) {
        return MAX_QUESTIONS_PER_STAGE.getOrDefault(stage, 10);
    }

    // ==================== 公共 API ====================

    public InterviewMessageEntity generateFirstQuestion(InterviewEntity interview, ResumeProfile profile) {
        CompanyEnum company = safeParseCompany(interview.getCompany());
        PositionEnum position = safeParsePosition(interview.getPosition());
        InterviewStageEnum stage = interview.getCurrentStage();

        String systemPrompt = promptBuilder.buildSystemPrompt(stage, company, position, profile, List.of());
        String userMessage = "面试官：你好，请候选人做自我介绍。";

        String question = aiClient.chat(systemPrompt, userMessage);

        var message = new InterviewMessageEntity();
        message.setInterviewId(interview.getId());
        message.setRole(MessageRoleEnum.AI);
        message.setContent(question);
        message.setQuestionType(STAGE_QUESTION_TYPE.get(stage));
        message.setSequenceNum(1);
        message.setTokenCount(0);

        log.info("生成面试首问: interviewId={}, stage={}, question={}", interview.getId(), stage, question);
        return message;
    }

    public EngineResult processAnswer(
            InterviewEntity interview,
            ResumeProfile profile,
            InterviewMessageEntity userMessage,
            List<InterviewMessageEntity> history
    ) {
        CompanyEnum company = safeParseCompany(interview.getCompany());
        PositionEnum position = safeParsePosition(interview.getPosition());
        InterviewStageEnum currentStage = interview.getCurrentStage();

        long stageAiCount = history.stream()
                .filter(m -> m.getRole() == MessageRoleEnum.AI)
                .filter(m -> m.getQuestionType() == STAGE_QUESTION_TYPE.get(currentStage))
                .count();

        int minForStage = currentStage == InterviewStageEnum.REVERSE_QA ? 1 : MIN_EXCHANGES_PER_STAGE;
        boolean meetMin = stageAiCount >= minForStage;

        boolean aiDeclaredComplete = history.stream()
                .filter(m -> m.getRole() == MessageRoleEnum.AI)
                .filter(m -> m.getQuestionType() == STAGE_QUESTION_TYPE.get(currentStage))
                .reduce((first, second) -> second)
                .map(m -> detectRoundComplete(m.getContent()))
                .orElse(false);

        boolean stageCompleted = (meetMin && aiDeclaredComplete)
                || (stageAiCount >= getMaxQuestionsForStage(currentStage));

        if (stageCompleted) {
            InterviewStageEnum nextStage = currentStage.next();
            if (nextStage == InterviewStageEnum.COMPLETED) {
                log.info("面试完成，总分将由异步评分聚合生成: interviewId={}", interview.getId());
                return new EngineResult(
                        getFallbackClosingRemark(currentStage),
                        STAGE_QUESTION_TYPE.get(currentStage),
                        true, true, InterviewStageEnum.COMPLETED,
                        null, null, null, null, null, null,
                        null
                );
            }

            String systemPrompt = promptBuilder.buildSystemPrompt(nextStage, company, position, profile, toMessageList(history));
            String userMessageText = switch (nextStage) {
                case PROJECT_DEEP_DIVE -> "面试官：好的，我们换个方向。聊聊你做过的项目经历吧，比如你最自豪的一个项目，你在里面扮演什么角色？";
                case TECH_ASSESSMENT -> "面试官：项目聊得差不多了，我们来聊聊技术基础。请就你应聘的岗位提出一个技术问题。";
                case REVERSE_QA -> "面试官：技术部分就到这。现在轮到你问我了，你可以随便问，关于团队、业务、技术栈、成长空间什么都行。";
                default -> "面试官：好的，我们进入下一阶段：" + nextStage.getDisplayName() + "。请给出第一个问题。";
            };
            String nextQuestion = aiClient.chat(systemPrompt, userMessageText);

            log.info("阶段完成: {} -> {}, interviewId={}", currentStage, nextStage, interview.getId());

            return new EngineResult(
                    nextQuestion,
                    STAGE_QUESTION_TYPE.get(nextStage),
                    true,
                    false,
                    nextStage,
                    null,
                    null, null, null, null, null,
                    null
            );
        }

        String systemPrompt = promptBuilder.buildSystemPrompt(currentStage, company, position, profile, toMessageList(history));
        String userMessageText = currentStage == InterviewStageEnum.REVERSE_QA
                ? "候选人：" + userMessage.getContent() + "\n\n面试官：请回答候选人的这个问题，不要向他提出任何新问题。"
                : "候选人：" + userMessage.getContent() + "\n\n面试官：请追问下一个问题。";
        String followUp = aiClient.chat(systemPrompt, userMessageText);

        log.info("追问生成: stage={}, interviewId={}", currentStage, interview.getId());

        return new EngineResult(
                followUp,
                STAGE_QUESTION_TYPE.get(currentStage),
                false,
                false,
                null,
                null, null, null, null, null, null,
                null
        );
    }

    public EngineResult processAnswerStream(
            InterviewEntity interview,
            ResumeProfile profile,
            InterviewMessageEntity userMessage,
            List<InterviewMessageEntity> history,
            SseEmitter emitter
    ) {
        CompanyEnum company = safeParseCompany(interview.getCompany());
        PositionEnum position = safeParsePosition(interview.getPosition());
        InterviewStageEnum currentStage = interview.getCurrentStage();

        long stageAiCount = history.stream()
                .filter(m -> m.getRole() == MessageRoleEnum.AI)
                .filter(m -> m.getQuestionType() == STAGE_QUESTION_TYPE.get(currentStage))
                .count();

        boolean forceEnd = stageAiCount >= getMaxQuestionsForStage(currentStage);
        int minForStage = currentStage == InterviewStageEnum.REVERSE_QA ? 1 : MIN_EXCHANGES_PER_STAGE;
        boolean meetMin = stageAiCount >= minForStage;

        boolean aiDeclaredComplete = history.stream()
                .filter(m -> m.getRole() == MessageRoleEnum.AI)
                .filter(m -> m.getQuestionType() == STAGE_QUESTION_TYPE.get(currentStage))
                .reduce((first, second) -> second)
                .map(m -> detectRoundComplete(m.getContent()))
                .orElse(false);

        boolean stageCompleted = meetMin && aiDeclaredComplete;

        if (forceEnd) {
            String systemPrompt = promptBuilder.buildSystemPrompt(currentStage, company, position, profile, toMessageList(history));
            String endInstruction = buildControlSystemPrompt("END");
            String userMessageText = "面试官：本轮面试已达到最大问题数量限制，请总结并结束。";

            StringBuilder fullPrompt = new StringBuilder(systemPrompt).append("\n\n").append(endInstruction);
            if (currentStage == InterviewStageEnum.REVERSE_QA) {
                fullPrompt.append("\n\n【强制结束规则】当前处于「反问环节」，你已到达本轮最大问题数。")
                        .append("你不得向候选人提出任何问题，不得追问候选人。")
                        .append("必须直接给出本轮面试的结束语和简短评价，然后自然结束对话。");
            }

            String response;
            try {
                response = streamAndAccumulate(
                        fullPrompt.toString(),
                        List.of(Map.of("role", "user", "content", userMessageText)),
                        emitter
                );
            } catch (Exception e) {
                log.warn("AI 调用失败，使用兜底结束语: interviewId={}", interview.getId(), e);
                response = getFallbackClosingRemark(currentStage);
            }
            log.info("达到题数上限强制结束: interviewId={}, stage={}", interview.getId(), currentStage);
            return new EngineResult(
                    response,
                    STAGE_QUESTION_TYPE.get(currentStage),
                    true,
                    true,
                    InterviewStageEnum.COMPLETED,
                    null, null, null, null, null, null,
                    null
            );
        }

        if (stageCompleted) {
            InterviewStageEnum nextStage = currentStage.next();
            if (nextStage == InterviewStageEnum.COMPLETED) {
                log.info("面试完成，总分将由异步评分聚合生成: interviewId={}", interview.getId());
                return new EngineResult(
                        getFallbackClosingRemark(currentStage),
                        STAGE_QUESTION_TYPE.get(currentStage),
                        true, true, InterviewStageEnum.COMPLETED,
                        null, null, null, null, null, null,
                        null
                );
            }

            String systemPrompt = promptBuilder.buildSystemPrompt(nextStage, company, position, profile, toMessageList(history));
            String userMessageText = switch (nextStage) {
                case PROJECT_DEEP_DIVE -> "面试官：好的，我们换个方向。聊聊你做过的项目经历吧，比如你最自豪的一个项目，你在里面扮演什么角色？";
                case TECH_ASSESSMENT -> "面试官：项目聊得差不多了，我们来聊聊技术基础。请就你应聘的岗位提出一个技术问题。";
                case REVERSE_QA -> "面试官：技术部分就到这。现在轮到你问我了，你可以随便问，关于团队、业务、技术栈、成长空间什么都行。";
                default -> "面试官：好的，我们进入下一阶段：" + nextStage.getDisplayName() + "。请给出第一个问题。";
            };
            String nextQuestion;
            try {
                nextQuestion = streamAndAccumulate(systemPrompt, List.of(Map.of("role", "user", "content", userMessageText)), emitter);
            } catch (Exception e) {
                log.warn("AI 调用失败，使用兜底问题: interviewId={}", interview.getId(), e);
                nextQuestion = getFallbackQuestion(currentStage);
            }

            log.info("阶段完成: {} -> {}, interviewId={}", currentStage, nextStage, interview.getId());

            return new EngineResult(
                    nextQuestion,
                    STAGE_QUESTION_TYPE.get(nextStage),
                    true,
                    false,
                    nextStage,
                    null, null, null, null, null, null,
                    null
            );
        }

        String systemPrompt = promptBuilder.buildSystemPrompt(currentStage, company, position, profile, toMessageList(history));
        String userMessageText = currentStage == InterviewStageEnum.REVERSE_QA
                ? "候选人：" + userMessage.getContent() + "\n\n面试官：请回答候选人的这个问题，不要向他提出任何新问题。"
                : "候选人：" + userMessage.getContent() + "\n\n面试官：请追问下一个问题。";
        String followUp;
        try {
            followUp = streamAndAccumulate(systemPrompt, List.of(Map.of("role", "user", "content", userMessageText)), emitter);
        } catch (Exception e) {
            log.warn("AI 调用失败，使用兜底问题: interviewId={}", interview.getId(), e);
            followUp = getFallbackQuestion(currentStage);
        }

        log.info("追问生成: stage={}, interviewId={}", currentStage, interview.getId());

        return new EngineResult(
                followUp,
                STAGE_QUESTION_TYPE.get(currentStage),
                false,
                false,
                null,
                null, null, null, null, null, null,
                null
        );
    }

    public EngineResult completeInterview(ResumeProfile profile, List<InterviewMessageEntity> history, CompanyEnum company, PositionEnum position) {
        log.info("面试所有阶段完成，生成评估");

        String systemPrompt = buildScoringPrompt(company, position, profile, history);
        String userMessage = "请根据以上面试对话，给出5个维度的评分和改进建议。输出JSON格式："
                + "{\"scoreTech\":X,\"scoreExpression\":X,\"scoreProject\":X,\"scoreLogic\":X,\"scoreAdaptability\":X,"
                + "\"suggestion\":\"...\",\"closingRemark\":\"...\"}";

        String response = aiClient.chat(systemPrompt, userMessage, "json_object");

        try {
            JsonNode root = new ObjectMapper().readTree(response);
            int scoreTech = root.path("scoreTech").asInt(60);
            int scoreExpression = root.path("scoreExpression").asInt(60);
            int scoreProject = root.path("scoreProject").asInt(60);
            int scoreLogic = root.path("scoreLogic").asInt(60);
            int scoreAdaptability = root.path("scoreAdaptability").asInt(60);
            int scoreTotal = Math.min(100, Math.round(0.25f * scoreTech + 0.20f * scoreExpression + 0.20f * scoreProject + 0.20f * scoreLogic + 0.15f * scoreAdaptability));
            String suggestion = root.path("suggestion").asText("");
            String closingRemark = root.path("closingRemark").asText("");

            return new EngineResult(
                    closingRemark,
                    null,
                    true,
                    true,
                    InterviewStageEnum.COMPLETED,
                    scoreTotal,
                    scoreTech, scoreExpression, scoreProject, scoreLogic, scoreAdaptability,
                    suggestion
            );
        } catch (Exception e) {
            log.error("解析评分JSON失败: {}", response, e);
            return fallbackScore();
        }
    }

    private EngineResult fallbackScore() {
        return new EngineResult(
                "面试结束，感谢你的参与。",
                null,
                true,
                true,
                InterviewStageEnum.COMPLETED,
                60,
                60, 60, 60, 60, 60,
                "面试结束，感谢你的参与。"
        );
    }

    public record QuestionScore(
            String questionDescription,
            Integer score,
            String grade,
            String feedback
    ) {}

    private static final Map<String, int[]> GRADE_RANGES = Map.of(
            "EXCELLENT", new int[]{80, 100},
            "GOOD", new int[]{70, 79},
            "AVERAGE", new int[]{60, 69},
            "POOR", new int[]{0, 59}
    );

    public QuestionScore scoreQuestion(String questionText, QuestionTypeEnum questionType, String candidateAnswer) {
        QuestionScore result = doScoreQuestion(questionText, questionType, candidateAnswer);
        if (result != null && !isGradeScoreConsistent(result.grade(), result.score())) {
            log.warn("题级评分 grade/score 不一致，触发重评: grade={}, score={}", result.grade(), result.score());
            QuestionScore retry = doScoreQuestion(questionText, questionType, candidateAnswer);
            if (retry != null && isGradeScoreConsistent(retry.grade(), retry.score())) {
                return retry;
            }
            int corrected = correctScoreByGrade(result.grade(), result.score());
            log.warn("重评后仍不一致，修正 score: original={}, corrected={}", result.score(), corrected);
            return new QuestionScore(result.questionDescription(), corrected, result.grade(), result.feedback());
        }
        return result;
    }

    private QuestionScore doScoreQuestion(String questionText, QuestionTypeEnum questionType, String candidateAnswer) {
        String typeName = questionType != null ? questionType.name() : "综合";
        String userMessage = "题目类型：" + typeName + "\n\n"
                + "题目内容：" + questionText + "\n\n"
                + "候选人回答：" + (candidateAnswer != null && !candidateAnswer.isBlank() ? candidateAnswer : "（未提供）") + "\n\n"
                + "【评分标准】\n"
                + "EXCELLENT (80-100)：回答完全切题，结构清晰，有深度，无事实错误，优于预期\n"
                + "GOOD (70-79)：回答切题，逻辑通顺，覆盖核心要点，少量细节可补充\n"
                + "AVERAGE (60-69)：回答基本切题，有主要观点，但存在遗漏或表述不够严谨\n"
                + "POOR (0-59)：回答部分切题或严重偏题，逻辑松散，有明显知识漏洞或答非所问\n\n"
                + "grade 必须与 score 严格对应。输出JSON（不要包含其他内容）："
                + "{\"questionDescription\":\"...\",\"score\":X,\"grade\":\"EXCELLENT/GOOD/AVERAGE/POOR\",\"feedback\":\"...\"}";

        String response = aiClient.chat(
                "你是一位严格的面试评分官。请根据评分标准打分，并确保 grade 与 score 严格对应。",
                userMessage,
                "json_object"
        );

        try {
            JsonNode root = new ObjectMapper().readTree(response);
            String description = root.path("questionDescription").asText("");
            int score = Math.min(100, Math.max(0, root.path("score").asInt(60)));
            String grade = root.path("grade").asText(null);
            String feedback = root.path("feedback").asText("");
            return new QuestionScore(description, score, grade, feedback);
        } catch (Exception e) {
            log.error("解析题级评分JSON失败: {}", response, e);
            return new QuestionScore("", 60, null, "评分解析失败");
        }
    }

    private boolean isGradeScoreConsistent(String grade, int score) {
        if (grade == null || grade.isBlank()) {
            return true;
        }
        int[] range = GRADE_RANGES.get(grade.toUpperCase());
        if (range == null) {
            return true;
        }
        return score >= range[0] && score <= range[1];
    }

    private int correctScoreByGrade(String grade, int originalScore) {
        if (grade == null || grade.isBlank()) {
            return originalScore;
        }
        int[] range = GRADE_RANGES.get(grade.toUpperCase());
        if (range == null) {
            return originalScore;
        }
        return Math.min(range[1], Math.max(range[0], originalScore));
    }

    // ==================== 私有方法 ====================

    private String streamAndAccumulate(String systemPrompt, List<Map<String, String>> messages, SseEmitter emitter) {
        try {
            return aiClient.chatStream(systemPrompt, messages, emitter).join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Error err) {
                throw err;
            }
            throw new RuntimeException("AI 流式调用失败: " + cause.getMessage(), cause);
        }
    }

    private String getFallbackQuestion(InterviewStageEnum stage) {
        String[] questions = FALLBACK_QUESTIONS.get(stage);
        if (questions == null || questions.length == 0) return "请继续回答。";
        return questions[(int) (System.nanoTime() % questions.length)];
    }

    private String getFallbackClosingRemark(InterviewStageEnum stage) {
        return switch (stage) {
            case REVERSE_QA -> "感谢你的提问，祝你面试顺利，我们后续再联系。";
            default -> "本轮面试结束，感谢你的参与。";
        };
    }

    private String buildScoringPrompt(CompanyEnum company, PositionEnum position, ResumeProfile profile, List<InterviewMessageEntity> history) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是").append(company.getDisplayName()).append("的").append(position.getDisplayName()).append("面试官。\n");
        sb.append("面试风格：").append(company.getStyleDescription()).append("\n\n");
        sb.append("【候选人简历】\n");
        sb.append(promptBuilder.buildProfileText(profile));

        sb.append("\n【完整面试对话】\n");
        for (InterviewMessageEntity msg : history) {
            String role = msg.getRole() == MessageRoleEnum.USER ? "候选人" : "面试官";
            sb.append(role).append("：").append(stripRoundCompleteToken(msg.getContent())).append("\n");
        }

        sb.append("\n请给出5个维度的评分（0-100）：技术深度、表达能力、项目经验、逻辑思维、适应性。");
        sb.append("\n同时给出总体改进建议和结束语。");
        sb.append("\n输出JSON格式，不要包含其他内容。");

        return sb.toString();
    }

    private List<Map<String, String>> toMessageList(List<InterviewMessageEntity> history) {
        List<Map<String, String>> msgs = new ArrayList<>();
        for (InterviewMessageEntity msg : history) {
            String role = switch (msg.getRole()) {
                case USER -> "user";
                case AI -> "assistant";
                default -> "system";
            };
            msgs.add(Map.of("role", role, "content", msg.getContent()));
        }
        return msgs;
    }

    private CompanyEnum safeParseCompany(String company) {
        if (company == null || company.isBlank()) {
            throw new BadRequestException("公司类型不能为空");
        }
        try {
            return CompanyEnum.valueOf(company.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("不支持的公司类型: " + company);
        }
    }

    private PositionEnum safeParsePosition(String position) {
        if (position == null || position.isBlank()) {
            throw new BadRequestException("职位类型不能为空");
        }
        try {
            return PositionEnum.valueOf(position.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("不支持的职位类型: " + position);
        }
    }

    public boolean detectRoundComplete(String aiResponse) {
        if (aiResponse == null || aiResponse.isBlank()) return false;
        String trimmed = aiResponse.trim();
        return trimmed.endsWith(ROUND_COMPLETE_TOKEN) || trimmed.contains("\n" + ROUND_COMPLETE_TOKEN);
    }

    /**
     * 兜底清理：移除 AI 回复中可能残留的"本轮/面试结束"特殊标记（如 [ROUND_COMPLETE]、[ROUND COMPLETED]、[ROUND-COMPLETE] 等），
     * 并压缩因移除而产生的多余空行。
     *
     * @param aiResponse 原始 AI 回复
     * @return 清理后的回复
     */
    public static String stripRoundCompleteToken(String aiResponse) {
        if (aiResponse == null || aiResponse.isBlank()) {
            return aiResponse;
        }
        String stripped = ROUND_COMPLETE_PATTERN.matcher(aiResponse).replaceAll("");
        stripped = stripped.replaceAll("\\n{3,}", "\n\n").trim();
        return stripped;
    }

    public String buildControlSystemPrompt(String command) {
        return switch (command.toUpperCase()) {
            case "SKIP" -> "【控制指令：跳过】请自然过渡到下一个问题，不要提及这是切换。";
            case "HINT" -> "【控制指令：提示】请给候选人一个思路提示，不要直接给出答案。";
            case "END" -> "【控制指令：结束】请简短总结本轮面试，给出优缺点各一句评价，然后自然结束对话，不要输出任何特殊标记或占位符。";
            default -> "";
        };
    }

    public EngineResult processControlCommand(
            InterviewEntity interview,
            ResumeProfile profile,
            String command,
            List<InterviewMessageEntity> history,
            SseEmitter emitter
    ) {
        CompanyEnum company = safeParseCompany(interview.getCompany());
        PositionEnum position = safeParsePosition(interview.getPosition());
        InterviewStageEnum currentStage = interview.getCurrentStage();

        String systemPrompt = promptBuilder.buildSystemPrompt(currentStage, company, position, profile, toMessageList(history));
        String controlInstruction = buildControlSystemPrompt(command);
        String userMessageText = switch (command.toUpperCase()) {
            case "SKIP" -> "面试官：请直接给出下一个问题。";
            case "HINT" -> "面试官：候选人请求提示，请给一个思路提示。";
            case "END" -> "面试官：请总结本轮面试并结束。";
            default -> "面试官：请继续。";
        };

        String response;
        try {
            response = streamAndAccumulate(
                    systemPrompt + "\n\n" + controlInstruction,
                    List.of(Map.of("role", "user", "content", userMessageText)),
                    emitter
            );
        } catch (Exception e) {
            log.warn("AI 调用失败，使用兜底问题: interviewId={}", interview.getId(), e);
            response = getFallbackQuestion(currentStage);
        }

        boolean isEnd = "END".equalsIgnoreCase(command);
        boolean roundCompleted = isEnd || detectRoundComplete(response);
        InterviewStageEnum nextStage = isEnd ? InterviewStageEnum.COMPLETED : null;

        log.info("控制指令: command={}, interviewId={}, roundCompleted={}", command, interview.getId(), roundCompleted);

        return new EngineResult(
                response,
                STAGE_QUESTION_TYPE.get(currentStage),
                isEnd,
                roundCompleted && isEnd,
                nextStage,
                null, null, null, null, null, null,
                null
        );
    }

    // ==================== record ====================

    public record EngineResult(
            String aiResponse,
            QuestionTypeEnum questionType,
            boolean stageCompleted,
            boolean interviewCompleted,
            InterviewStageEnum nextStage,
            Integer scoreTotal,
            Integer scoreTech,
            Integer scoreExpression,
            Integer scoreProject,
            Integer scoreLogic,
            Integer scoreAdaptability,
            String suggestion
    ) {}
}
