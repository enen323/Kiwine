package com.prepdeck.question.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.prepdeck.question.domain.UserFavoriteEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserFavoriteMapper extends BaseMapper<UserFavoriteEntity> {

    UserFavoriteEntity findByUserAndQuestion(@Param("userId") Long userId,
                                             @Param("questionId") Long questionId);

    int countByQuestion(@Param("questionId") Long questionId);

    long countByUser(@Param("userId") Long userId);
}
