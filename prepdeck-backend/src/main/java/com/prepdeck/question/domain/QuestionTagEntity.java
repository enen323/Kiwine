package com.prepdeck.question.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("question_tag")
public class QuestionTagEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("question_id")
    private Long questionId;

    @TableField("tag_id")
    private Long tagId;
}
