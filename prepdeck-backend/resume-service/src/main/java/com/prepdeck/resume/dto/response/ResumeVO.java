package com.prepdeck.resume.dto.response;

import com.prepdeck.resume.domain.ResumeEntity;

import java.time.LocalDateTime;
import java.util.List;

public record ResumeVO(
        Long id,
        Long userId,
        String fileName,
        String fileUrl,
        String contentText,
        String contentJson,
        Boolean isActive,
        String title,
        String template,
        String language,
        String themeConfig,
        Boolean isPublic,
        String shareToken,
        Integer viewCount,
        List<SectionVO> sections,
        LocalDateTime createTime,
        LocalDateTime updateTime
) {
    public static ResumeVO fromEntity(ResumeEntity entity) {
        return new ResumeVO(
                entity.getId(),
                entity.getUserId(),
                entity.getFileName(),
                entity.getFileUrl(),
                entity.getContentText(),
                entity.getContentJson(),
                entity.getIsActive(),
                entity.getTitle(),
                entity.getTemplate(),
                entity.getLanguage(),
                entity.getThemeConfig(),
                entity.getIsPublic(),
                entity.getShareToken(),
                entity.getViewCount(),
                null,
                entity.getCreateTime(),
                entity.getUpdateTime()
        );
    }
}
