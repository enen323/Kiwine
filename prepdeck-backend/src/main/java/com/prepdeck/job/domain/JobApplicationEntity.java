package com.prepdeck.job.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("job_applications")
public class JobApplicationEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private Long userId;

    private String resumeId;

    private String company;

    private String position;

    private String channel;

    private String status;

    private LocalDateTime appliedAt;

    private String notes;

    @TableField(fill = FieldFill.INSERT, value = "created_at")
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE, value = "updated_at")
    private LocalDateTime updateTime;
}
