package com.prepdeck.interview.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;

/**
 * 消息角色枚举
 */
public enum MessageRoleEnum {

    AI("AI"),
    USER("USER");

    @EnumValue
    private final String value;

    MessageRoleEnum(String value) {
        this.value = value;
    }
}
