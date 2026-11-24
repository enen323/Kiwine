package com.prepdeck.interview.dto.request;

import com.prepdeck.interview.enums.CompanyEnum;
import com.prepdeck.interview.enums.PositionEnum;
import jakarta.validation.constraints.NotNull;

/**
 * 开始面试请求
 */
public record StartInterviewRequest(

        @NotNull(message = "公司类型不能为空")
        CompanyEnum companyType,

        @NotNull(message = "职位不能为空")
        PositionEnum position,

        Long resumeId
) {}
