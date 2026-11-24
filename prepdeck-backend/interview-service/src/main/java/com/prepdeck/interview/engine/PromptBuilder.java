package com.prepdeck.interview.engine;

import com.prepdeck.interview.config.InterviewPromptProperties;
import com.prepdeck.interview.dto.model.ResumeProfile;
import com.prepdeck.interview.enums.CompanyEnum;
import com.prepdeck.interview.enums.InterviewStageEnum;
import com.prepdeck.interview.enums.PositionEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 面试 Prompt 构建器。
 *
 * <p>模板骨架保留在代码中，动态内容（考察重点、题数上限、阶段规则、全局边界）
 * 从 {@link InterviewPromptProperties} 读取，支持 Nacos 热更新。</p>
 */
@Component
@RequiredArgsConstructor
public class PromptBuilder {

    private final InterviewPromptProperties promptProperties;

    private static final String SYSTEM_PROMPT_SKELETON = """
            # 角色设定
            你是 %s，%s。

            ## 个人背景
            %s

            ## 性格特征
            %s

            ## 提问风格
            %s

            ---
            # 面试上下文
            ## 本轮考察重点
            %s

            ## 招聘岗位 JD
            %s 岗位。

            ## 候选人简历
            %s
            ---
            # 面试执行规范
            ## 对话节奏
            - 每次只提出一个问题，等候选人完整作答后再回应。
            - 先对候选人的回答做出简短回应（认可、质疑或补充追问），再过渡到下一个问题。
            - 本轮总共约 __MAX_QUESTIONS__ 个主题问题（含追问），节奏自然，不赶不拖。

            ## 提问策略
            - 开场：简短自我介绍 + 本轮方向说明，随即进入第一个问题。
            - 追问应基于候选人的实际回答内容，探查深度和真实性，而不是机械展开预设问题。
            - 当候选人已经给出充分、到位的回答时，不要为了追问而追问，自然过渡到下一主题。
            - 问题之间要有逻辑关联或自然过渡，避免给人"逐条念清单"的感觉。

            ## 交互风格
            - 说话像一个有经验的面试官，而非 AI 助手。保持自然口语化表达。
            - 可以有个人反应：对好的回答表示认可（"嗯，这个思路不错"），对含糊的回答直接追问（"等一下，这里能展开说说吗"）。
            - 不使用"让我们来探讨"、"非常好的问题"、"感谢你的分享"等模板化客套话。
            - 不使用 emoji。

            ## 结束规则
            - 问题差不多聊完时，给出简短、真实的本轮评价（优缺点各一句即可，像面试官心里真正想的那样），然后自然结束对话。
            - 当你判断本轮考察重点已经聊透、候选人回答充分时，可以在回复末尾加上 [ROUND_COMPLETE] 标记，表示此阶段可以结束。不要滥用，只有当你确实觉得已经了解足够、没有更多值得追问的内容时才使用。
            - 在「自我介绍」和「反问环节」阶段，如果你觉得已经获得了足够信息，可以更主动地结束本轮，不必等到达到最大问题数。

            ## 处理未知技术概念
            - 候选人可能提到你知识库中没有的新技术、框架或工具。这是正常的——技术领域发展极快。
            - 绝对不要质疑某个技术是否存在，不要说"据我所知这个还没有发布"之类的话。
            - 正确做法：把它当作你不太熟悉的新事物，聚焦于候选人如何使用它、为何选择它、解决了什么问题。

            ## 核心原则
            你是一位经验丰富的真人面试官，有自己的专业判断和风格偏好。你的每一个反应都基于角色的真实感受和专业素养，而非在"执行面试任务"。
            """;

    public String buildSystemPrompt(
            InterviewStageEnum stage,
            CompanyEnum company,
            PositionEnum position,
            ResumeProfile profile,
            List<Map<String, String>> history
    ) {
        InterviewPromptProperties.StageConfig stageConfig = resolveStageConfig(stage);

        String focusAreas = stageConfig.getFocus();
        int maxQuestions = stageConfig.getMaxQuestions();

        String prompt = SYSTEM_PROMPT_SKELETON.formatted(
                company.getInterviewerName(),
                company.getTitle(),
                company.getBio(),
                company.getPersonality(),
                company.getStyleDescription(),
                focusAreas,
                position.getDisplayName(),
                buildProfileText(profile)
        ).replace("__MAX_QUESTIONS__", String.valueOf(maxQuestions));

        // 追加全局阶段边界规则
        String globalBoundaries = promptProperties.getGlobalBoundaries();
        if (globalBoundaries != null && !globalBoundaries.isBlank()) {
            prompt += "\n" + globalBoundaries;
        }

        // 追加阶段特别附录
        String appendix = resolveAppendix(stageConfig);
        if (appendix != null) {
            prompt += "\n" + appendix;
        }

        StringBuilder sb = new StringBuilder(prompt);
        sb.append("\n## 面试当前阶段\n").append(resolveStageDesc(stage, position)).append("\n");

        if (history != null && !history.isEmpty()) {
            sb.append("\n【完整对话历史】\n");
            for (Map<String, String> msg : history) {
                String role = switch (msg.get("role")) {
                    case "assistant", "system" -> "面试官";
                    case "user" -> "候选人";
                    default -> msg.get("role");
                };
                sb.append(role).append("：").append(msg.get("content")).append("\n");
            }
        }

        return sb.toString();
    }

    private InterviewPromptProperties.StageConfig resolveStageConfig(InterviewStageEnum stage) {
        Map<String, InterviewPromptProperties.StageConfig> stages = promptProperties.getStages();
        if (stages != null && stages.containsKey(stage.name())) {
            InterviewPromptProperties.StageConfig config = stages.get(stage.name());
            if (config.getFocus() != null) {
                return config;
            }
        }
        // 降级默认值，保证 Nacos 未推送时仍可运行
        InterviewPromptProperties.StageConfig fallback = new InterviewPromptProperties.StageConfig();
        fallback.setFocus("综合能力");
        fallback.setMaxQuestions(10);
        return fallback;
    }

    private String resolveAppendix(InterviewPromptProperties.StageConfig stageConfig) {
        if (stageConfig.getAppendix() == null || stageConfig.getAppendix().isBlank()) {
            return null;
        }
        Map<String, String> snippets = promptProperties.getRuleSnippets();
        if (snippets == null) {
            return null;
        }
        String appendix = snippets.get(stageConfig.getAppendix());
        return appendix != null && !appendix.isBlank() ? appendix : null;
    }

    private String resolveStageDesc(InterviewStageEnum stage, PositionEnum position) {
        return switch (stage) {
            case SELF_INTRO -> "自我介绍（了解候选人背景）";
            case PROJECT_DEEP_DIVE -> "项目深挖（深入询问简历中的项目经验）";
            case TECH_ASSESSMENT -> "技术考核（考察%s技术基础）".formatted(position.getDisplayName());
            case REVERSE_QA -> "反问环节（候选人提问）";
            case COMPLETED -> "面试结束";
        };
    }

    public String buildProfileText(ResumeProfile profile) {
        if (profile == null) {
            // 「不使用简历」场景：候选人未提供简历，提示 AI 仅依据对话内容评估与提问
            return "（候选人未提供简历，请仅基于面试对话中的信息进行评估与提问，不要假设或引用简历内容。）";
        }

        StringBuilder sb = new StringBuilder();

        if (profile.summary() != null && !profile.summary().isBlank()) {
            sb.append("个人概述：").append(profile.summary()).append("\n\n");
        }

        if (!profile.education().isEmpty()) {
            sb.append("教育经历：\n");
            for (ResumeProfile.EducationItem edu : profile.education()) {
                sb.append("- ").append(edu.field() != null && !edu.field().isBlank() ? edu.field() : "未知专业");
                if (!edu.highlights().isEmpty()) {
                    sb.append("，").append(String.join("、", edu.highlights()));
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        if (!profile.projects().isEmpty()) {
            sb.append("项目经历：\n");
            for (ResumeProfile.ProjectItem proj : profile.projects()) {
                sb.append("- ").append(proj.name() != null ? proj.name() : "未命名项目");
                if (!proj.technologies().isEmpty()) {
                    sb.append(" [").append(String.join(", ", proj.technologies())).append("]");
                }
                if (!proj.highlights().isEmpty()) {
                    sb.append(" — ").append(String.join("；", proj.highlights()));
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        if (!profile.skills().isEmpty()) {
            sb.append("专业技能：\n");
            for (ResumeProfile.SkillCategory cat : profile.skills()) {
                sb.append("- ").append(cat.name()).append("：").append(String.join("、", cat.skills())).append("\n");
            }
        }

        return sb.toString();
    }
}
