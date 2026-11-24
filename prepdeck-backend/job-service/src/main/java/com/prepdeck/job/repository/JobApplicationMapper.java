package com.prepdeck.job.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.prepdeck.job.domain.JobApplicationEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface JobApplicationMapper extends BaseMapper<JobApplicationEntity> {

    default Page<JobApplicationEntity> findPageByUserId(Page<JobApplicationEntity> page,
                                                         Long userId,
                                                         String status,
                                                         String keyword) {
        LambdaQueryWrapper<JobApplicationEntity> wrapper = new LambdaQueryWrapper<JobApplicationEntity>()
                .eq(JobApplicationEntity::getUserId, userId);

        if (status != null && !status.isBlank()) {
            wrapper.eq(JobApplicationEntity::getStatus, status);
        }

        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w
                    .like(JobApplicationEntity::getCompany, keyword)
                    .or()
                    .like(JobApplicationEntity::getPosition, keyword));
        }

        wrapper.orderByDesc(JobApplicationEntity::getAppliedAt);
        return selectPage(page, wrapper);
    }

    default List<JobApplicationEntity> findByIds(@Param("ids") List<String> ids) {
        return selectBatchIds(ids);
    }
}
