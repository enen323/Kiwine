package com.prepdeck.interview.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.prepdeck.interview.domain.InterviewQuestionScoreEntity;
import com.prepdeck.interview.enums.QuestionTypeEnum;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Delete;

import java.util.List;

public interface InterviewQuestionScoreMapper extends BaseMapper<InterviewQuestionScoreEntity> {

    default List<InterviewQuestionScoreEntity> findByInterviewId(Long interviewId) {
        return selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<InterviewQuestionScoreEntity>()
                .eq(InterviewQuestionScoreEntity::getInterviewId, interviewId));
    }

    default List<InterviewQuestionScoreEntity> findByInterviewIds(List<Long> interviewIds) {
        if (interviewIds == null || interviewIds.isEmpty()) {
            return List.of();
        }
        return selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<InterviewQuestionScoreEntity>()
                .in(InterviewQuestionScoreEntity::getInterviewId, interviewIds));
    }

    default List<InterviewQuestionScoreEntity> findByInterviewIdAndType(Long interviewId, QuestionTypeEnum questionType) {
        return selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<InterviewQuestionScoreEntity>()
                .eq(InterviewQuestionScoreEntity::getInterviewId, interviewId)
                .eq(InterviewQuestionScoreEntity::getQuestionType, questionType));
    }

    @Delete("DELETE FROM interview_question_score WHERE interview_id = #{interviewId}")
    int hardDeleteByInterviewId(Long interviewId);
}
