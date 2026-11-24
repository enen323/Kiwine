package com.prepdeck.resume.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.prepdeck.resume.domain.ResumeEntity;

import java.util.List;

public interface ResumeMapper extends BaseMapper<ResumeEntity> {

    default List<ResumeEntity> findByUserId(Long userId) {
        LambdaQueryWrapper<ResumeEntity> wrapper = new LambdaQueryWrapper<ResumeEntity>()
                .eq(ResumeEntity::getUserId, userId)
                .orderByDesc(ResumeEntity::getUpdateTime);
        return selectList(wrapper);
    }
}
