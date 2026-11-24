package com.prepdeck.question.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.prepdeck.question.domain.DailyChallengeEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DailyChallengeMapper extends BaseMapper<DailyChallengeEntity> {

    DailyChallengeEntity findToday(@Param("userId") Long userId,
                                   @Param("today") java.time.LocalDate today);

    int upsert(DailyChallengeEntity entity);
}
