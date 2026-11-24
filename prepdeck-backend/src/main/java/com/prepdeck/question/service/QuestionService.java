package com.prepdeck.question.service;

import com.prepdeck.question.domain.QuestionEntity;
import com.prepdeck.question.domain.TagEntity;
import com.prepdeck.question.dto.CreateQuestionRequest;
import com.prepdeck.question.dto.QuestionDetailVO;
import com.prepdeck.question.dto.QuestionListRequest;
import com.prepdeck.question.dto.UpdateQuestionRequest;

import java.util.List;

public interface QuestionService {

    List<QuestionDetailVO> listQuestions(QuestionListRequest request, long total);

    List<QuestionDetailVO> searchQuestions(String keyword, int page, int pageSize, long total);

    QuestionDetailVO getQuestion(Long id);

    QuestionDetailVO createQuestion(CreateQuestionRequest request);

    QuestionDetailVO updateQuestion(Long id, UpdateQuestionRequest request);

    void deleteQuestion(Long id);

    long countQuestions(QuestionListRequest request);

    long countSearch(String keyword);

    QuestionDetailVO toDetailVO(QuestionEntity entity, List<com.prepdeck.question.domain.TagEntity> tags, boolean isFavorited);
}
