package com.prepdeck.job.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record JobApplicationCreateRequest(
        @NotBlank(message = "请填写目标公司")
        @Size(max = 255, message = "公司名称不能超过 255 个字符")
        String company,

        @NotBlank(message = "请填写职位名称")
        @Size(max = 255, message = "职位名称不能超过 255 个字符")
        String position,

        @NotBlank(message = "请选择投递状态")
        String status,

        @Size(max = 64, message = "投递渠道不能超过 64 个字符")
        String channel,

        String resumeId,

        LocalDateTime appliedAt,

        @Size(max = 4000, message = "备注不能超过 4000 个字符")
        String notes
) {
}
