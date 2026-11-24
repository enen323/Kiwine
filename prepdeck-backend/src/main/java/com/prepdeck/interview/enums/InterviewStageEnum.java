package com.prepdeck.interview.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

/**
 * 面试阶段枚举
 */
@Getter
public enum InterviewStageEnum {

    SELF_INTRO("自我介绍", "SELF_INTRO"),
    PROJECT_DEEP_DIVE("项目深挖", "PROJECT_DEEP_DIVE"),
    TECH_ASSESSMENT("技术考核", "TECH_ASSESSMENT"),
    REVERSE_QA("反问环节", "REVERSE_QA"),
    COMPLETED("面试结束", "COMPLETED");

    private final String displayName;

    @EnumValue
    private final String value;

    InterviewStageEnum(String displayName, String value) {
        this.displayName = displayName;
        this.value = value;
    }

    /**
     * 获取下一阶段
     */
    public InterviewStageEnum next() {
        return switch (this) {
            case SELF_INTRO -> PROJECT_DEEP_DIVE;
            case PROJECT_DEEP_DIVE -> TECH_ASSESSMENT;
            case TECH_ASSESSMENT -> REVERSE_QA;
            case REVERSE_QA -> COMPLETED;
            case COMPLETED -> COMPLETED;
        };
    }
}
