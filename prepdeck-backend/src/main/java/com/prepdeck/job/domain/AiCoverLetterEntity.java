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
@TableName("ai_cover_letters")
public class AiCoverLetterEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private Long userId;

    private String resumeId;

    private String applicationId;

    private String company;

    private String position;

    private String jobDescription;

    private String extraNotes;

    private String outputLanguage;

    private String title;

    private String body;

    @TableField(fill = FieldFill.INSERT, value = "created_at")
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE, value = "updated_at")
    private LocalDateTime updateTime;
}
