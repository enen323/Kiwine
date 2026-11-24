package com.prepdeck.common.enums;

/**
 * 通用启用/禁用状态枚举
 */
public enum StatusEnum {

    ENABLED(1, "启用"),
    DISABLED(0, "禁用");

    private final int value;
    private final String label;

    StatusEnum(int value, String label) {
        this.value = value;
        this.label = label;
    }

    public int getValue() {
        return value;
    }

    public String getLabel() {
        return label;
    }
}
