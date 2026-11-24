package com.prepdeck.interview.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;

public enum ControlCommand {

    SKIP("SKIP"),
    HINT("HINT"),
    END("END");

    @EnumValue
    private final String value;

    ControlCommand(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
