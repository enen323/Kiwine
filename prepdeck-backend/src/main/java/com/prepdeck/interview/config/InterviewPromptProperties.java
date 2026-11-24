package com.prepdeck.interview.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 面试 Prompt 配置。
 *
 * <p>从 Nacos / application.yml 读取，支持按阶段配置考察重点、禁止事项、附录规则等。</p>
 */
@Component
@ConfigurationProperties(prefix = "interview.prompt")
@Data
public class InterviewPromptProperties {

    /**
     * Prompt 版本号，用于审计和 A/B 实验。
     */
    private String version = "v2";

    /**
     * 各阶段配置。
     * key 为 InterviewStageEnum 的 name()，例如 SELF_INTRO。
     */
    private Map<String, StageConfig> stages;

    /**
     * 可复用的规则片段（附录）。
     * key 为阶段附录标识，例如 self_intro_rules。
     */
    private Map<String, String> ruleSnippets = Map.of();

    /**
     * 全局阶段边界规则，追加到主 System Prompt 末尾。
     */
    private String globalBoundaries = "";

    /**
     * 单个阶段的 Prompt 配置。
     */
    @Data
    public static class StageConfig {
        /**
         * 本轮考察重点描述。
         */
        private String focus;

        /**
         * 该阶段最大问题数（含追问）。
         */
        private Integer maxQuestions = 10;

        /**
         * 自我介绍阶段禁止追问的主题列表。
         */
        private List<String> forbidTopics = List.of();

        /**
         * 关联的附录规则 key（对应 ruleSnippets 中的 key）。
         * 如果为空，则不追加阶段特别规则。
         */
        private String appendix;

        /**
         * 是否允许该阶段在获得足够信息后主动结束。
         * 自我介绍和反问环节通常开启。
         */
        private Boolean proactiveEnd = false;
    }
}
