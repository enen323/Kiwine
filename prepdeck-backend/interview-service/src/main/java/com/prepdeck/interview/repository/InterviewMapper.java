package com.prepdeck.interview.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.prepdeck.interview.domain.InterviewEntity;
import com.prepdeck.interview.enums.InterviewStatusEnum;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;

import java.util.List;

/**
 * 面试记录 Mapper
 */
public interface InterviewMapper extends BaseMapper<InterviewEntity> {

    /**
     * 按用户ID查询面试记录，按创建时间降序排列
     */
    default List<InterviewEntity> findByUserId(Long userId) {
        LambdaQueryWrapper<InterviewEntity> wrapper = Wrappers.lambdaQuery(InterviewEntity.class)
                .eq(InterviewEntity::getUserId, userId)
                .orderByDesc(InterviewEntity::getCreateTime);
        return selectList(wrapper);
    }

    /**
     * 查询用户进行中的面试记录（排除已完成和已终止）
     */
    default List<InterviewEntity> findActiveByUserId(Long userId) {
        LambdaQueryWrapper<InterviewEntity> wrapper = Wrappers.lambdaQuery(InterviewEntity.class)
                .eq(InterviewEntity::getUserId, userId)
                .notIn(InterviewEntity::getStatus, InterviewStatusEnum.COMPLETED, InterviewStatusEnum.TERMINATED)
                .orderByDesc(InterviewEntity::getCreateTime);
        return selectList(wrapper);
    }

    /**
     * 按主键查询并加行锁（FOR UPDATE），用于原子化获取消息序号
     */
    @Select("SELECT * FROM interview_record WHERE id = #{id} AND deleted = false FOR UPDATE")
    InterviewEntity selectByIdForUpdate(Long id);

    @Delete("DELETE FROM interview_record WHERE id = #{id}")
    int hardDeleteById(Long id);
}
