package com.prepdeck.question.service;

import com.prepdeck.question.domain.DailyChallengeEntity;
import com.prepdeck.question.domain.QuestionEntity;
import com.prepdeck.question.dto.DailyChallengeVO;
import com.prepdeck.question.dto.QuestionDetailVO;
import com.prepdeck.question.repository.DailyChallengeMapper;
import com.prepdeck.question.repository.QuestionMapper;
import com.prepdeck.question.repository.QuestionTagMapper;
import com.prepdeck.question.repository.TagMapper;
import com.prepdeck.common.enums.ErrorCode;
import com.prepdeck.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class DailyChallengeServiceImpl implements DailyChallengeService {

    private final DailyChallengeMapper dailyChallengeMapper;
    private final QuestionMapper questionMapper;
    private final QuestionTagMapper questionTagMapper;
    private final TagMapper tagMapper;
    private final QuestionService questionService;

    @Override
    @Transactional
    public DailyChallengeVO getTodayChallenge() {
        Long userId = com.prepdeck.common.util.SecurityContext.getCurrentUserId();
        LocalDate today = LocalDate.now();

        DailyChallengeEntity exist = dailyChallengeMapper.findToday(userId, today);
        if (exist != null) {
            QuestionEntity question = questionMapper.selectById(exist.getQuestionId());
            if (question == null || Boolean.TRUE.equals(question.getDeleted())) {
                throw new NotFoundException(ErrorCode.DAILY_CHALLENGE_NOT_FOUND);
            }
            return buildVO(exist, question, exist.getCompleted());
        }

        List<QuestionEntity> randomCandidates = questionMapper.selectRandom(10);
        if (randomCandidates.isEmpty()) {
            throw new NotFoundException(ErrorCode.DAILY_CHALLENGE_NOT_FOUND);
        }

        QuestionEntity selected = randomCandidates.get(0);

        DailyChallengeEntity dc = new DailyChallengeEntity();
        dc.setUserId(userId);
        dc.setQuestionId(selected.getId());
        dc.setChallengeDate(today);
        dc.setCompleted(false);
        dailyChallengeMapper.upsert(dc);

        DailyChallengeEntity todayRecord = dailyChallengeMapper.findToday(userId, today);
        if (todayRecord == null) {
            todayRecord = dc;
        }

        return buildVO(todayRecord, selected, false);
    }

    @Override
    @Transactional
    public DailyChallengeVO completeTodayChallenge() {
        Long userId = com.prepdeck.common.util.SecurityContext.getCurrentUserId();
        LocalDate today = LocalDate.now();

        DailyChallengeEntity exist = dailyChallengeMapper.findToday(userId, today);
        if (exist == null) {
            DailyChallengeVO vo = getTodayChallenge();
            exist = dailyChallengeMapper.findToday(userId, today);
            if (exist == null) {
                throw new NotFoundException(ErrorCode.DAILY_CHALLENGE_NOT_FOUND);
            }
        }

        if (Boolean.TRUE.equals(exist.getCompleted())) {
            QuestionEntity question = questionMapper.selectById(exist.getQuestionId());
            if (question == null || Boolean.TRUE.equals(question.getDeleted())) {
                throw new NotFoundException(ErrorCode.DAILY_CHALLENGE_NOT_FOUND);
            }
            return buildVO(exist, question, true);
        }

        exist.setCompleted(true);
        dailyChallengeMapper.updateById(exist);

        QuestionEntity question = questionMapper.selectById(exist.getQuestionId());
        if (question == null || Boolean.TRUE.equals(question.getDeleted())) {
            throw new NotFoundException(ErrorCode.DAILY_CHALLENGE_NOT_FOUND);
        }

        return buildVO(exist, question, true);
    }

    private DailyChallengeVO buildVO(DailyChallengeEntity dc, QuestionEntity question, boolean completed) {
        List<Long> tagIds = questionTagMapper.findTagIdsByQuestionId(question.getId());
        List<com.prepdeck.question.domain.TagEntity> tags = tagIds.isEmpty()
                ? List.of()
                : tagMapper.selectBatchIds(tagIds).stream()
                .filter(t -> !Boolean.TRUE.equals(t.getDeleted()))
                .toList();

        QuestionDetailVO questionVO = questionService.toDetailVO(question, tags, false);
        return new DailyChallengeVO(dc.getId(), questionVO, completed);
    }
}
