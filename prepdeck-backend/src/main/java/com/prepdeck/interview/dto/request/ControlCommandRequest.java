package com.prepdeck.interview.dto.request;

import com.prepdeck.interview.enums.ControlCommand;
import jakarta.validation.constraints.NotNull;

public record ControlCommandRequest(
        @NotNull(message = "控制指令不能为空")
        ControlCommand command
) {}
