package com.prepdeck.interview.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.prepdeck.interview.enums.InterviewStageEnum;
import com.prepdeck.interview.enums.InterviewStatusEnum;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 面试记录实体
 */
@Data
@TableName("interview_record")
public class InterviewEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 关联简历ID */
    private Long resumeId;

    /** 用户ID */
    private Long userId;

    /** 公司标识 */
    private String company;

    /** 职位标识 */
    private String position;

    /** 面试状态 */
    private InterviewStatusEnum status;

    /** 当前阶段 */
    private InterviewStageEnum currentStage;

    /** 阶段内题目序号 */
    private Integer stageOrder;

    /** 总题目数 */
    private Integer totalQuestions = 0;

    /** 总分（可空） */
    private Integer scoreTotal;

    /** 技术分 */
    private Integer scoreTech;

    /** 表达分 */
    private Integer scoreExpression;

    /** 项目分 */
    private Integer scoreProject;

    /** 逻辑分 */
    private Integer scoreLogic;

    /** 应变分 */
    private Integer scoreAdaptability;

    /** 面试评语 */
    private String suggestion;

    /** 面试开始时间 */
    private LocalDateTime startedAt;

    /** 面试结束时间 */
    private LocalDateTime completedAt;

    /** 持续时长（秒） */
    private Integer durationSeconds;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 逻辑删除标记 */
    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Boolean deleted;

    /** 乐观锁版本号 */
    @Version
    @TableField(fill = FieldFill.INSERT)
    private Integer version;
}
