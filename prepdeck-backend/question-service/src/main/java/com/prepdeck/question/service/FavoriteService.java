package com.prepdeck.question.service;

import com.prepdeck.question.dto.QuestionDetailVO;
import com.prepdeck.question.dto.QuestionListRequest;

import java.util.List;

public interface FavoriteService {

    List<QuestionDetailVO> listFavorites(int page, int pageSize);

    long countFavorites(Long userId);

    void favorite(Long questionId);

    void unfavorite(Long questionId);
}
