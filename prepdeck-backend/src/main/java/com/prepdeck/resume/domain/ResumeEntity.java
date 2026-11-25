package com.prepdeck.resume.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.prepdeck.resume.handler.JsonbTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 简历实�?
 *
 * <p>对应数据库表 interview.resume�?/p>
 */
@Data
@TableName("resume")
public class ResumeEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private String fileName;

    private String fileUrl;

    private String contentText;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private String contentJson;

    private Boolean isActive;

    private String title;

    private String template;

    private String language;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private String themeConfig;

    private Boolean isPublic;

    private String shareToken;

    private String sharePassword;

    private Integer viewCount;

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
