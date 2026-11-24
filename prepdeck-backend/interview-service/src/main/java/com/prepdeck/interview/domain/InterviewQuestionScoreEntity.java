package com.prepdeck.interview.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.prepdeck.interview.enums.QuestionTypeEnum;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("interview_question_score")
public class InterviewQuestionScoreEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long interviewId;
    private Long messageId;
    private QuestionTypeEnum questionType;
    private String questionDescription;
    private Integer score;
    private String feedback;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(fill = FieldFill.INSERT)
    private Boolean deleted;

    @TableField(fill = FieldFill.INSERT)
    private Integer version;
}
