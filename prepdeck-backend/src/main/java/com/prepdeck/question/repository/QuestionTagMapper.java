package com.prepdeck.question.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.prepdeck.question.domain.QuestionTagEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface QuestionTagMapper extends BaseMapper<QuestionTagEntity> {

    List<Long> findTagIdsByQuestionId(@Param("questionId") Long questionId);

    List<QuestionTagEntity> findTagIdsByQuestionIds(@Param("questionIds") List<Long> questionIds);

    List<Long> findQuestionIdsByTagId(@Param("tagId") Long tagId);

    int countDistinctTagsByQuestionIds(@Param("questionIds") List<Long> questionIds,
                                       @Param("tagIds") List<Long> tagIds);
}
