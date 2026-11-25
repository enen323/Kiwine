package com.prepdeck.quota.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.prepdeck.quota.domain.UserQuotaEntity;
import org.apache.ibatis.annotations.Param;

public interface UserQuotaMapper extends BaseMapper<UserQuotaEntity> {

    int incrementInterviewFreeUsed(@Param("userId") Long userId);

    int incrementResumeChatFreeUsed(@Param("userId") Long userId);

    int tryConsumeInterviewFree(@Param("userId") Long userId, @Param("limit") int limit);

    int tryConsumeResumeChatFree(@Param("userId") Long userId, @Param("limit") int limit);

    int incrementInterviewPaid(@Param("userId") Long userId);

    int incrementResumeChatPaid(@Param("userId") Long userId);

    int insertIgnore(@Param("userId") Long userId);

    int resetInterviewFreeUsed();

    int resetResumeChatFreeUsed();

    int decrementInterviewPaid(@Param("userId") Long userId);

    int decrementResumeChatPaid(@Param("userId") Long userId);

    int consumeInterview(@Param("userId") Long userId, @Param("limit") int limit);

    int consumeResumeChat(@Param("userId") Long userId, @Param("limit") int limit);

    int tryConsumeInterviewPaid(@Param("userId") Long userId);

    int tryConsumeResumeChatPaid(@Param("userId") Long userId);
}
