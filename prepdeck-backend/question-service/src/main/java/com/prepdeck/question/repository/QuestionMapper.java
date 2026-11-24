package com.prepdeck.question.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.prepdeck.question.domain.QuestionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface QuestionMapper extends BaseMapper<QuestionEntity> {

    List<QuestionEntity> searchByVector(@Param("keyword") String keyword,
                                        @Param("offset") long offset,
                                        @Param("limit") int limit);

    long countSearchByVector(@Param("keyword") String keyword);

    List<QuestionEntity> selectRandom(@Param("limit") int limit);

    List<QuestionEntity> selectQuestionList(@Param("type") String type,
                                            @Param("difficulty") String difficulty,
                                            @Param("tagIds") List<Long> tagIds,
                                            @Param("keyword") String keyword,
                                            @Param("sortBy") String sortBy,
                                            @Param("order") String order,
                                            @Param("limit") int limit,
                                            @Param("offset") long offset);

    long countQuestionList(@Param("type") String type,
                           @Param("difficulty") String difficulty,
                           @Param("tagIds") List<Long> tagIds,
                           @Param("keyword") String keyword);

    List<QuestionEntity> selectRecommendationList(@Param("types") List<String> types,
                                                  @Param("excludeTitles") List<String> excludeTitles,
                                                  @Param("sortBy") String sortBy,
                                                  @Param("order") String order,
                                                  @Param("limit") int limit,
                                                  @Param("offset") long offset);
}
