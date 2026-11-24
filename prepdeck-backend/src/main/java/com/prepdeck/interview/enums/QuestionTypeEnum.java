package com.prepdeck.interview.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;

/**
 * 题目类型枚举
 */
public enum QuestionTypeEnum {

    SELF_INTRO("SELF_INTRO"),
    TECH_PROJECT("TECH_PROJECT"),
    TECH_BASIC("TECH_BASIC"),
    ALGORITHM("ALGORITHM"),
    SYSTEM_DESIGN("SYSTEM_DESIGN"),
    BEHAVIORAL("BEHAVIORAL"),
    REVERSE_QA("REVERSE_QA");

    @EnumValue
    private final String value;

    QuestionTypeEnum(String value) {
        this.value = value;
    }
}
