package com.prepdeck.question.service;

import com.prepdeck.question.domain.UserPracticeRecordEntity;

public interface UserPracticeRecordService {

    void recordPractice(Long userId, Long questionId, String sourceType, Long sourceRefId);

    boolean hasPracticed(Long userId, Long questionId);
}
