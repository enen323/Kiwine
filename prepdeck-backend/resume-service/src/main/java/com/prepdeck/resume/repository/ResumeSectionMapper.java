package com.prepdeck.resume.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.prepdeck.resume.domain.ResumeSectionEntity;

import java.util.List;

public interface ResumeSectionMapper extends BaseMapper<ResumeSectionEntity> {

    default List<ResumeSectionEntity> findByResumeId(Long resumeId) {
        return selectList(new LambdaQueryWrapper<ResumeSectionEntity>()
                .eq(ResumeSectionEntity::getResumeId, resumeId)
                .orderByAsc(ResumeSectionEntity::getSortOrder));
    }

    default int deleteByResumeId(Long resumeId) {
        return delete(new LambdaQueryWrapper<ResumeSectionEntity>()
                .eq(ResumeSectionEntity::getResumeId, resumeId));
    }
}
