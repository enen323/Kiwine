package com.prepdeck.question.service;

import com.prepdeck.question.domain.QuestionEntity;
import com.prepdeck.question.domain.QuestionTagEntity;
import com.prepdeck.question.domain.UserFavoriteEntity;
import com.prepdeck.question.dto.QuestionDetailVO;
import com.prepdeck.question.repository.QuestionMapper;
import com.prepdeck.question.repository.QuestionTagMapper;
import com.prepdeck.question.repository.TagMapper;
import com.prepdeck.question.repository.UserFavoriteMapper;
import com.prepdeck.common.enums.ErrorCode;
import com.prepdeck.common.exception.BadRequestException;
import com.prepdeck.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class FavoriteServiceImpl implements FavoriteService {

    private final UserFavoriteMapper userFavoriteMapper;
    private final QuestionMapper questionMapper;
    private final QuestionTagMapper questionTagMapper;
    private final TagMapper tagMapper;
    private final QuestionService questionService;

    @Override
    @Transactional(readOnly = true)
    public long countFavorites(Long userId) {
        return userFavoriteMapper.countByUser(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuestionDetailVO> listFavorites(int page, int pageSize) {
        Long userId = com.prepdeck.common.util.SecurityContext.getCurrentUserId();
        long offset = (page - 1) * (long) pageSize;

        List<UserFavoriteEntity> favs = userFavoriteMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserFavoriteEntity>()
                        .eq(UserFavoriteEntity::getUserId, userId)
                        .eq(UserFavoriteEntity::getDeleted, false)
                        .last("LIMIT " + pageSize + " OFFSET " + offset)
        );

        List<Long> questionIds = favs.stream()
                .map(UserFavoriteEntity::getQuestionId)
                .toList();

        if (questionIds.isEmpty()) {
            return List.of();
        }

        List<QuestionEntity> questions = questionMapper.selectBatchIds(questionIds).stream()
                .filter(q -> !Boolean.TRUE.equals(q.getDeleted()))
                .toList();

        List<QuestionTagEntity> questionTags = questionTagMapper.findTagIdsByQuestionIds(
                questions.stream().map(QuestionEntity::getId).toList()
        );
        Map<Long, List<Long>> questionTagMap = questionTags.stream()
                .collect(Collectors.groupingBy(QuestionTagEntity::getQuestionId,
                        Collectors.mapping(QuestionTagEntity::getTagId, Collectors.toList())));
        Map<Long, List<com.prepdeck.question.domain.TagEntity>> tagMap = fetchTags(questionTagMap);

        Map<Long, UserFavoriteEntity> favMap = favs.stream()
                .collect(Collectors.toMap(UserFavoriteEntity::getQuestionId, f -> f));

        return questions.stream()
                .map(q -> questionService.toDetailVO(q, tagMap.getOrDefault(q.getId(), List.of()), true))
                .toList();
    }

    @Override
    public void favorite(Long questionId) {
        Long userId = com.prepdeck.common.util.SecurityContext.getCurrentUserId();

        QuestionEntity question = questionMapper.selectById(questionId);
        if (question == null || Boolean.TRUE.equals(question.getDeleted())) {
            throw new NotFoundException(ErrorCode.QUESTION_NOT_FOUND);
        }

        UserFavoriteEntity exist = userFavoriteMapper.findByUserAndQuestion(userId, questionId);
        if (exist != null && !Boolean.TRUE.equals(exist.getDeleted())) {
            throw new BadRequestException(ErrorCode.QUESTION_ALREADY_FAVORITED);
        }

        UserFavoriteEntity fav = new UserFavoriteEntity();
        fav.setUserId(userId);
        fav.setQuestionId(questionId);
        userFavoriteMapper.insert(fav);

        question.setFavorites((question.getFavorites() == null ? 0 : question.getFavorites()) + 1);
        questionMapper.updateById(question);
    }

    @Override
    public void unfavorite(Long questionId) {
        Long userId = com.prepdeck.common.util.SecurityContext.getCurrentUserId();

        QuestionEntity question = questionMapper.selectById(questionId);
        if (question == null || Boolean.TRUE.equals(question.getDeleted())) {
            throw new NotFoundException(ErrorCode.QUESTION_NOT_FOUND);
        }

        UserFavoriteEntity exist = userFavoriteMapper.findByUserAndQuestion(userId, questionId);
        if (exist == null || Boolean.TRUE.equals(exist.getDeleted())) {
            throw new BadRequestException(ErrorCode.QUESTION_NOT_FAVORITED);
        }

        exist.setDeleted(true);
        userFavoriteMapper.updateById(exist);

        question.setFavorites(Math.max(0, (question.getFavorites() == null ? 0 : question.getFavorites()) - 1));
        questionMapper.updateById(question);
    }

    private Map<Long, List<com.prepdeck.question.domain.TagEntity>> fetchTags(Map<Long, List<Long>> questionTagMap) {
        List<Long> allTagIds = questionTagMap.values().stream()
                .flatMap(List::stream)
                .distinct()
                .toList();

        if (allTagIds.isEmpty()) {
            return Map.of();
        }

        List<com.prepdeck.question.domain.TagEntity> allTags = tagMapper.selectBatchIds(allTagIds);
        Map<Long, com.prepdeck.question.domain.TagEntity> tagMap = allTags.stream()
                .filter(t -> !Boolean.TRUE.equals(t.getDeleted()))
                .collect(Collectors.toMap(com.prepdeck.question.domain.TagEntity::getId, t -> t));

        return questionTagMap.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().stream()
                                .map(tagMap::get)
                                .filter(t -> t != null)
                                .toList()
                ));
    }
}
