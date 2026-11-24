package com.prepdeck.resume.dto.response;

import com.prepdeck.resume.domain.ResumeSectionEntity;

import java.time.LocalDateTime;

public record SectionVO(
        Long id,
        Long resumeId,
        String type,
        String title,
        Integer sortOrder,
        Boolean visible,
        String content,
        LocalDateTime createTime,
        LocalDateTime updateTime
) {
    public static SectionVO fromEntity(ResumeSectionEntity entity) {
        return new SectionVO(
                entity.getId(),
                entity.getResumeId(),
                entity.getType(),
                entity.getTitle(),
                entity.getSortOrder(),
                entity.getVisible(),
                entity.getContent(),
                entity.getCreateTime(),
                entity.getUpdateTime()
        );
    }
}
