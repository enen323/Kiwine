package com.prepdeck.interview.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.prepdeck.interview.domain.InterviewMessageEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 面试消息 Mapper
 */
public interface InterviewMessageMapper extends BaseMapper<InterviewMessageEntity> {

    /**
     * 按面试ID查询消息列表，按序号升序排列
     */
    default List<InterviewMessageEntity> findByInterviewId(Long interviewId) {
        return findByInterviewId(interviewId, null);
    }

    /**
     * 按面试ID查询消息列表（滑动窗口），按序号升序排列
     *
     * @param interviewId 面试ID
     * @param limit       最多返回条数，null 表示不限制
     */
    default List<InterviewMessageEntity> findByInterviewId(Long interviewId, Integer limit) {
        LambdaQueryWrapper<InterviewMessageEntity> wrapper = Wrappers.lambdaQuery(InterviewMessageEntity.class)
                .eq(InterviewMessageEntity::getInterviewId, interviewId)
                .eq(InterviewMessageEntity::getDeleted, false)
                .orderByDesc(InterviewMessageEntity::getSequenceNum);
        if (limit != null) {
            wrapper.last("LIMIT " + limit);
        }
        List<InterviewMessageEntity> result = selectList(wrapper);
        if (result != null && !result.isEmpty()) {
            java.util.Collections.reverse(result);
        }
        return result;
    }

    /**
     * 统计面试消息总数
     */
    default Long countByInterviewId(Long interviewId) {
        LambdaQueryWrapper<InterviewMessageEntity> wrapper = Wrappers.lambdaQuery(InterviewMessageEntity.class)
                .eq(InterviewMessageEntity::getInterviewId, interviewId)
                .eq(InterviewMessageEntity::getDeleted, false);
        return selectCount(wrapper);
    }

    @Select("SELECT COALESCE(MAX(sequence_num), 0) FROM interview_message WHERE interview_id = #{interviewId} AND deleted = false")
    Integer selectMaxSeq(Long interviewId);

    @Delete("DELETE FROM interview_message WHERE interview_id = #{interviewId}")
    int hardDeleteByInterviewId(Long interviewId);

    @Delete("DELETE FROM interview_message WHERE id = #{id}")
    int hardDeleteById(Long id);
}
