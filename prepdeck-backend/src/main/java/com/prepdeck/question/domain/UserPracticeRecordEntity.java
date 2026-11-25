package com.prepdeck.question.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("user_practice_record")
public class UserPracticeRecordEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("question_id")
    private Long questionId;

    @TableField("source_type")
    private String sourceType;

    @TableField("source_ref_id")
    private Long sourceRefId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime practicedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Boolean deleted;

    @TableField(fill = FieldFill.INSERT)
    @Version
    private Integer version;
}
