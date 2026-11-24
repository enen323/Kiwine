package com.prepdeck.question.service;

import com.prepdeck.question.domain.UserPracticeRecordEntity;
import com.prepdeck.question.repository.UserPracticeRecordMapper;
import com.prepdeck.common.util.SecurityContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class UserPracticeRecordServiceImpl implements UserPracticeRecordService {

    private final UserPracticeRecordMapper userPracticeRecordMapper;

    @Override
    @Transactional(readOnly = true)
    public boolean hasPracticed(Long userId, Long questionId) {
        return userPracticeRecordMapper.countByUserAndQuestion(userId, questionId) > 0;
    }

    @Override
    public void recordPractice(Long userId, Long questionId, String sourceType, Long sourceRefId) {
        if (hasPracticed(userId, questionId)) {
            return;
        }

        var record = new UserPracticeRecordEntity();
        record.setUserId(userId);
        record.setQuestionId(questionId);
        record.setSourceType(sourceType);
        record.setSourceRefId(sourceRefId);
        userPracticeRecordMapper.insert(record);

        log.debug("记录做题行为: userId={}, questionId={}, sourceType={}", userId, questionId, sourceType);
    }
}
