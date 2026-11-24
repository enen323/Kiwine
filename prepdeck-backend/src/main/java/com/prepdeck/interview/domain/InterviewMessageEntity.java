package com.prepdeck.interview.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.prepdeck.interview.enums.MessageRoleEnum;
import com.prepdeck.interview.enums.QuestionTypeEnum;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 面试消息实体
 */
@Data
@TableName("interview_message")
public class InterviewMessageEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 关联面试记录ID */
    private Long interviewId;

    /** 消息角色 */
    private MessageRoleEnum role;

    /** 消息内容 */
    private String content;

    /** 题目类型（可空，仅AI消息携带） */
    private QuestionTypeEnum questionType;

    /** 消息序号 */
    private Integer sequenceNum;

    /** Token消耗数 */
    private Integer tokenCount = 0;

    /** 创建时间（手动填充） */
    private LocalDateTime createdAt;

    /** 逻辑删除标记 */
    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Boolean deleted;
}
