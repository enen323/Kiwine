package com.prepdeck.interview.enums;

import lombok.Getter;

/**
 * 面试目标职位枚举
 */
@Getter
public enum PositionEnum {

    BACKEND("后端开发"),
    FRONTEND("前端开发"),
    ALGORITHM("算法工程师"),
    CLIENT("客户端开发"),
    PRODUCT("产品经理"),
    OPERATIONS("运营");

    private final String displayName;

    PositionEnum(String displayName) {
        this.displayName = displayName;
    }
}
