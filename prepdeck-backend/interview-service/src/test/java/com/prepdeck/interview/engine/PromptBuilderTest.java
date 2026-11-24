package com.prepdeck.interview.engine;

import com.prepdeck.interview.config.InterviewPromptProperties;
import com.prepdeck.interview.dto.model.ResumeProfile;
import com.prepdeck.interview.enums.CompanyEnum;
import com.prepdeck.interview.enums.InterviewStageEnum;
import com.prepdeck.interview.enums.PositionEnum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class PromptBuilderTest {

    private PromptBuilder builderWith(InterviewPromptProperties properties) {
        return new PromptBuilder(properties);
    }

    private InterviewPromptProperties defaultProperties() {
        InterviewPromptProperties properties = new InterviewPromptProperties();
        properties.setVersion("v2");
        properties.setGlobalBoundaries("""
                ## 阶段边界
                - 严格遵循当前阶段的考察重点，不要提前进入其他阶段的提问模式。
                - 每个阶段有各自的职责，请自觉守住边界。
                """);

        InterviewPromptProperties.StageConfig selfIntro = new InterviewPromptProperties.StageConfig();
        selfIntro.setFocus("个人背景、求职动机、职业规划（禁止追问项目细节）");
        selfIntro.setMaxQuestions(3);
        selfIntro.setForbidTopics(List.of("项目架构", "技术选型", "个人贡献", "难点解决"));
        selfIntro.setAppendix("self_intro_rules");
        selfIntro.setProactiveEnd(true);

        InterviewPromptProperties.StageConfig projectDeepDive = new InterviewPromptProperties.StageConfig();
        projectDeepDive.setFocus("项目架构设计、技术选型、个人贡献、问题解决");
        projectDeepDive.setMaxQuestions(8);

        InterviewPromptProperties.StageConfig techAssessment = new InterviewPromptProperties.StageConfig();
        techAssessment.setFocus("技术基础、算法、系统设计");
        techAssessment.setMaxQuestions(7);

        InterviewPromptProperties.StageConfig reverseQa = new InterviewPromptProperties.StageConfig();
        reverseQa.setFocus("候选人提问质量、关注点");
        reverseQa.setMaxQuestions(3);
        reverseQa.setAppendix("reverse_qa_rules");

        properties.setStages(Map.of(
                "SELF_INTRO", selfIntro,
                "PROJECT_DEEP_DIVE", projectDeepDive,
                "TECH_ASSESSMENT", techAssessment,
                "REVERSE_QA", reverseQa
        ));

        properties.setRuleSnippets(Map.of(
                "self_intro_rules", """
                        ---
                        # 自我介绍环节特别规则
                        当前处于「自我介绍」阶段。本轮考察重点是个人背景、求职动机、职业规划。
                        请你认真聆听候选人的自我介绍，判断是否已经获得了足够了解。
                        如果候选人介绍得已经比较充分，你可以简单回应后直接结束本轮，无需继续追问，在回复末尾加上 [ROUND_COMPLETE] 标记即可。
                        如果还有想深入了解的地方，再进行一两个追问。

                        ## 绝对禁止
                        - 不要追问项目架构、技术选型、个人贡献、难点解决等深挖类问题。
                        - 如果候选人提到了项目，可以简单回应（如"嗯，看到你有XX项目经验"），但不要展开追问项目细节。
                        - 项目相关问题统一留到「项目深挖」阶段再问。
                        """,
                "reverse_qa_rules", """
                        ---
                        # 反问环节特别规则
                        当前处于「反问环节（候选人提问）」。在此阶段，你**不得向候选人提出任何问题**，也不要追问候选人。
                        你的唯一职责是：认真聆听候选人提出的每一个问题，然后以面试官的身份给出真实、专业、简洁的回答。
                        回答时可以适当补充一点背景或建议，但保持自然口语化，不要长篇大论。
                        当候选人没有进一步问题时，可以自然结束本轮。
                        如果你觉得候选人的问题已经聊得差不多，也可以主动结束，在回复末尾加上 [ROUND_COMPLETE] 标记即可。
                        """
        ));

        return properties;
    }

    @Test
    void buildSystemPrompt_containsCompanyStyleAndPosition() {
        ResumeProfile profile = new ResumeProfile(
                List.of(new ResumeProfile.EducationItem("软件工程", List.of("一等奖学金"))),
                List.of(new ResumeProfile.ProjectItem("蜂购", List.of("Spring", "Redis"), List.of("QPS优化"))),
                List.of(new ResumeProfile.SkillCategory("Java", List.of("熟悉多线程"))),
                "学习能力强"
        );

        List<Map<String, String>> history = List.of(
                Map.of("role", "assistant", "content", "请自我介绍"),
                Map.of("role", "user", "content", "我是豆豆，后端开发")
        );

        String prompt = builderWith(defaultProperties()).buildSystemPrompt(
                InterviewStageEnum.SELF_INTRO,
                CompanyEnum.BYTE_DANCE,
                PositionEnum.BACKEND,
                profile,
                history
        );

        assertTrue(prompt.contains("张明"));
        assertTrue(prompt.contains("技术专家"));
        assertTrue(prompt.contains("字节跳动推荐系统负责人"));
        assertTrue(prompt.contains("直爽果断"));
        assertTrue(prompt.contains("追问多且尖锐"));
        assertTrue(prompt.contains("后端开发"));
        assertTrue(prompt.contains("个人背景、求职动机、职业规划（禁止追问项目细节）"));
        assertTrue(prompt.contains("软件工程"));
        assertTrue(prompt.contains("一等奖学金"));
        assertTrue(prompt.contains("蜂购"));
        assertTrue(prompt.contains("学习能力强"));
        assertTrue(prompt.contains("阶段边界"));
        assertTrue(prompt.contains("严格遵循当前阶段的考察重点"));
        assertTrue(prompt.contains("自我介绍环节特别规则"));
        assertTrue(prompt.contains("绝对禁止"));
        assertTrue(prompt.contains("不要追问项目架构、技术选型、个人贡献、难点解决等深挖类问题"));
        assertTrue(prompt.contains("项目相关问题统一留到「项目深挖」阶段再问"));
        assertTrue(prompt.contains("面试官：请自我介绍"));
        assertTrue(prompt.contains("候选人：我是豆豆，后端开发"));
    }

    @Test
    void buildSystemPrompt_emptyHistory_omitsHistorySection() {
        ResumeProfile profile = new ResumeProfile(
                List.of(),
                List.of(),
                List.of(),
                "学习能力强"
        );

        String prompt = builderWith(defaultProperties()).buildSystemPrompt(
                InterviewStageEnum.TECH_ASSESSMENT,
                CompanyEnum.ALIBABA,
                PositionEnum.BACKEND,
                profile,
                null
        );

        assertFalse(prompt.contains("【完整对话历史】"));
        assertTrue(prompt.contains("学习能力强"));
        assertTrue(prompt.contains("技术基础、算法、系统设计"));
        assertTrue(prompt.contains("阶段边界"));
    }

    @Test
    void buildSystemPrompt_roleMapping_correctlyMapsRoles() {
        ResumeProfile profile = new ResumeProfile(
                List.of(),
                List.of(),
                List.of(),
                ""
        );

        List<Map<String, String>> history = List.of(
                Map.of("role", "assistant", "content", "请自我介绍"),
                Map.of("role", "system", "content", "系统提示"),
                Map.of("role", "user", "content", "候选人回答")
        );

        String prompt = builderWith(defaultProperties()).buildSystemPrompt(
                InterviewStageEnum.SELF_INTRO,
                CompanyEnum.ALIBABA,
                PositionEnum.BACKEND,
                profile,
                history
        );

        assertTrue(prompt.contains("面试官：请自我介绍"));
        assertTrue(prompt.contains("面试官：系统提示"));
        assertTrue(prompt.contains("候选人：候选人回答"));
    }

    @Test
    void buildSystemPrompt_selfIntro_containsProactiveEndRule() {
        ResumeProfile profile = new ResumeProfile(
                List.of(),
                List.of(),
                List.of(),
                ""
        );

        String prompt = builderWith(defaultProperties()).buildSystemPrompt(
                InterviewStageEnum.SELF_INTRO,
                CompanyEnum.BYTE_DANCE,
                PositionEnum.BACKEND,
                profile,
                List.of()
        );

        assertTrue(prompt.contains("自我介绍环节特别规则"));
        assertTrue(prompt.contains("[ROUND_COMPLETE]"));
        assertTrue(prompt.contains("禁止追问项目细节"));
        assertTrue(prompt.contains("绝对禁止"));
        assertTrue(prompt.contains("不要追问项目架构、技术选型、个人贡献、难点解决等深挖类问题"));
        assertTrue(prompt.contains("项目相关问题统一留到「项目深挖」阶段再问"));
    }

    @Test
    void buildSystemPrompt_reverseQa_containsAnswerOnlyAndProactiveEndRule() {
        ResumeProfile profile = new ResumeProfile(
                List.of(),
                List.of(),
                List.of(),
                ""
        );

        String prompt = builderWith(defaultProperties()).buildSystemPrompt(
                InterviewStageEnum.REVERSE_QA,
                CompanyEnum.ALIBABA,
                PositionEnum.BACKEND,
                profile,
                List.of()
        );

        assertTrue(prompt.contains("反问环节特别规则"));
        assertTrue(prompt.contains("不得向候选人提出任何问题"));
        assertTrue(prompt.contains("[ROUND_COMPLETE]"));
    }

    @Test
    void buildSystemPrompt_fallbackWhenStageConfigMissing() {
        ResumeProfile profile = new ResumeProfile(
                List.of(),
                List.of(),
                List.of(),
                ""
        );

        InterviewPromptProperties properties = new InterviewPromptProperties();
        properties.setVersion("v2");
        properties.setStages(Map.of());
        properties.setRuleSnippets(Map.of());
        properties.setGlobalBoundaries("");

        String prompt = builderWith(properties).buildSystemPrompt(
                InterviewStageEnum.SELF_INTRO,
                CompanyEnum.BYTE_DANCE,
                PositionEnum.BACKEND,
                profile,
                List.of()
        );

        assertTrue(prompt.contains("本轮考察重点"));
        assertTrue(prompt.contains("综合能力"));
        assertFalse(prompt.contains("自我介绍环节特别规则"));
    }

    @Test
    void buildProfileText_nullProfile_returnsNoResumeNotice() {
        // 「不使用简历」场景：profile 为 null，应返回明确占位说明而非抛出 NPE
        String text = builderWith(defaultProperties()).buildProfileText(null);
        assertTrue(text.contains("候选人未提供简历"));
    }

    @Test
    void buildSystemPrompt_nullProfile_withoutResumeOption_doesNotThrow() {
        // 「不使用简历」选项：startInterview 中 resumeId 为 null -> profile 为 null，
        // 构建系统 prompt 时不得抛出 NullPointerException
        String prompt = builderWith(defaultProperties()).buildSystemPrompt(
                InterviewStageEnum.SELF_INTRO,
                CompanyEnum.BYTE_DANCE,
                PositionEnum.BACKEND,
                null,
                List.of()
        );

        assertTrue(prompt.contains("候选人未提供简历"));
        assertTrue(prompt.contains("后端开发"));
    }
}
