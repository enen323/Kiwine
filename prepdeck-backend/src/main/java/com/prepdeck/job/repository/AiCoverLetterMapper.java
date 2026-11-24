package com.prepdeck.job.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.prepdeck.job.domain.AiCoverLetterEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface AiCoverLetterMapper extends BaseMapper<AiCoverLetterEntity> {

    default List<AiCoverLetterEntity> findByResumeId(@Param("userId") Long userId,
                                                      @Param("resumeId") String resumeId) {
        LambdaQueryWrapper<AiCoverLetterEntity> wrapper = new LambdaQueryWrapper<AiCoverLetterEntity>()
                .eq(AiCoverLetterEntity::getUserId, userId)
                .eq(AiCoverLetterEntity::getResumeId, resumeId)
                .orderByDesc(AiCoverLetterEntity::getCreateTime);
        return selectList(wrapper);
    }

    default List<AiCoverLetterEntity> findByIds(@Param("ids") List<String> ids) {
        return selectBatchIds(ids);
    }
}
