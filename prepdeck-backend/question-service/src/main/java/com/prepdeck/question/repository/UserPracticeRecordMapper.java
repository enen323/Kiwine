package com.prepdeck.question.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.prepdeck.question.domain.UserPracticeRecordEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface UserPracticeRecordMapper extends BaseMapper<UserPracticeRecordEntity> {

    default List<UserPracticeRecordEntity> findRecentByUser(@Param("userId") Long userId,
                                                            @Param("limit") int limit) {
        return selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserPracticeRecordEntity>()
                .eq(UserPracticeRecordEntity::getUserId, userId)
                .eq(UserPracticeRecordEntity::getDeleted, false)
                .orderByDesc(UserPracticeRecordEntity::getPracticedAt)
                .last("LIMIT " + limit));
    }

    default long countByUserAndQuestion(@Param("userId") Long userId,
                                        @Param("questionId") Long questionId) {
        return selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserPracticeRecordEntity>()
                .eq(UserPracticeRecordEntity::getUserId, userId)
                .eq(UserPracticeRecordEntity::getQuestionId, questionId)
                .eq(UserPracticeRecordEntity::getDeleted, false));
    }

    default int deleteByUserAndQuestion(@Param("userId") Long userId,
                                        @Param("questionId") Long questionId) {
        return delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserPracticeRecordEntity>()
                .eq(UserPracticeRecordEntity::getUserId, userId)
                .eq(UserPracticeRecordEntity::getQuestionId, questionId));
    }
}
