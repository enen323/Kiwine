package com.prepdeck.question.domain;

import com.baomidou.mybatisplus.annotation.*;
import com.prepdeck.common.handler.JsonbTypeHandler;
import com.prepdeck.question.dto.AnswerVO;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("question")
public class QuestionEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("title")
    private String title;

    @TableField("content")
    private String content;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private AnswerVO answer;

    @TableField("type")
    private String type;

    @TableField("difficulty")
    private String difficulty;

    @TableField("easy_mistake")
    private String easyMistake;

    @TableField("interview_count")
    private Integer interviewCount;

    @TableField("favorites")
    private Integer favorites;

    @TableField("views")
    private Integer views;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Boolean deleted;

    @Version
    @TableField(fill = FieldFill.INSERT)
    private Integer version;
}
