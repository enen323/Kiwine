package com.prepdeck.interview.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;

/**
 * 面试状态枚举
 */
public enum InterviewStatusEnum {

    CREATED("CREATED"),
    IN_PROGRESS("IN_PROGRESS"),
    COMPLETED("COMPLETED"),
    TERMINATED("TERMINATED");

    @EnumValue
    private final String value;

    InterviewStatusEnum(String value) {
        this.value = value;
    }
}
