package com.prepdeck.question.service;

import com.prepdeck.question.dto.AnswerVO;
import com.prepdeck.question.dto.CreateQuestionRequest;
import com.prepdeck.question.dto.QuestionDetailVO;
import com.prepdeck.question.dto.QuestionListRequest;
import com.prepdeck.question.dto.UpdateQuestionRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prepdeck.question.repository.QuestionMapper;
import com.prepdeck.question.repository.QuestionTagMapper;
import com.prepdeck.question.repository.TagMapper;
import com.prepdeck.question.repository.UserFavoriteMapper;
import com.prepdeck.question.domain.QuestionEntity;
import com.prepdeck.question.domain.QuestionTagEntity;
import com.prepdeck.common.enums.ErrorCode;
import com.prepdeck.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class QuestionServiceImpl implements QuestionService {

    private final QuestionMapper questionMapper;
    private final QuestionTagMapper questionTagMapper;
    private final TagMapper tagMapper;
    private final UserFavoriteMapper userFavoriteMapper;

    @Override
    @Transactional(readOnly = true)
    public List<QuestionDetailVO> listQuestions(QuestionListRequest request, long total) {
        int page = request.page() != null && request.page() > 0 ? request.page() : 1;
        int pageSize = request.pageSize() != null && request.pageSize() > 0 ? request.pageSize() : 20;
        long offset = (page - 1) * pageSize;

        List<QuestionEntity> entities = questionMapper.selectQuestionList(
                request.type(),
                request.difficulty(),
                request.tagIds(),
                request.keyword(),
                request.sortBy(),
                request.order(),
                pageSize,
                offset
        );

        List<Long> questionIds = entities.stream()
                .map(QuestionEntity::getId)
                .toList();

        if (questionIds.isEmpty()) {
            return entities.stream()
                    .map(e -> toDetailVO(e, List.of(), false))
                    .toList();
        }

        List<QuestionTagEntity> questionTags = questionTagMapper.findTagIdsByQuestionIds(questionIds);
        Map<Long, List<Long>> questionTagMap = questionTags.stream()
                .collect(Collectors.groupingBy(QuestionTagEntity::getQuestionId,
                        Collectors.mapping(QuestionTagEntity::getTagId, Collectors.toList())));
        Map<Long, List<com.prepdeck.question.domain.TagEntity>> tagMap = fetchTags(questionTagMap);

        return entities.stream()
                .map(e -> toDetailVO(e, tagMap.getOrDefault(e.getId(), List.of()), false))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuestionDetailVO> searchQuestions(String keyword, int page, int pageSize, long total) {
        long offset = (page - 1) * (long) pageSize;
        List<QuestionEntity> entities = questionMapper.searchByVector(keyword, offset, pageSize);

        List<Long> questionIds = entities.stream()
                .map(QuestionEntity::getId)
                .toList();

        if (questionIds.isEmpty()) {
            return entities.stream()
                    .map(e -> toDetailVO(e, List.of(), false))
                    .toList();
        }

        List<QuestionTagEntity> questionTags = questionTagMapper.findTagIdsByQuestionIds(questionIds);
        Map<Long, List<Long>> questionTagMap = questionTags.stream()
                .collect(Collectors.groupingBy(QuestionTagEntity::getQuestionId,
                        Collectors.mapping(QuestionTagEntity::getTagId, Collectors.toList())));
        Map<Long, List<com.prepdeck.question.domain.TagEntity>> tagMap = fetchTags(questionTagMap);

        return entities.stream()
                .map(e -> toDetailVO(e, tagMap.getOrDefault(e.getId(), List.of()), false))
                .toList();
    }

    @Override
    public QuestionDetailVO getQuestion(Long id) {
        QuestionEntity entity = questionMapper.selectById(id);
        if (entity == null || Boolean.TRUE.equals(entity.getDeleted())) {
            throw new NotFoundException(ErrorCode.QUESTION_NOT_FOUND);
        }

        entity.setViews(entity.getViews() == null ? 1 : entity.getViews() + 1);
        questionMapper.updateById(entity);

        List<Long> tagIds = questionTagMapper.findTagIdsByQuestionId(id);
        List<com.prepdeck.question.domain.TagEntity> tags = tagIds.isEmpty()
                ? List.of()
                : tagMapper.selectBatchIds(tagIds).stream()
                .filter(t -> !Boolean.TRUE.equals(t.getDeleted()))
                .toList();

        return toDetailVO(entity, tags, false);
    }

    @Override
    public QuestionDetailVO createQuestion(CreateQuestionRequest request) {
        QuestionEntity entity = new QuestionEntity();
        entity.setTitle(request.title());
        entity.setContent(request.content());
        entity.setAnswer(toAnswerVO(request.answer()));
        entity.setType(request.type());
        entity.setDifficulty(request.difficulty());
        entity.setEasyMistake(request.easyMistake());
        entity.setInterviewCount(0);
        entity.setFavorites(0);
        entity.setViews(0);
        questionMapper.insert(entity);

        if (request.tagIds() != null && !request.tagIds().isEmpty()) {
            for (Long tagId : request.tagIds()) {
                QuestionTagEntity qt = new QuestionTagEntity();
                qt.setQuestionId(entity.getId());
                qt.setTagId(tagId);
                questionTagMapper.insert(qt);
            }
        }

        return toDetailVO(entity, List.of(), false);
    }

    @Override
    public QuestionDetailVO updateQuestion(Long id, UpdateQuestionRequest request) {
        QuestionEntity entity = questionMapper.selectById(id);
        if (entity == null || Boolean.TRUE.equals(entity.getDeleted())) {
            throw new NotFoundException(ErrorCode.QUESTION_NOT_FOUND);
        }

        entity.setTitle(request.title());
        entity.setContent(request.content());
        entity.setAnswer(toAnswerVO(request.answer()));
        entity.setType(request.type());
        entity.setDifficulty(request.difficulty());
        entity.setEasyMistake(request.easyMistake());
        questionMapper.updateById(entity);

        questionTagMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<QuestionTagEntity>()
                .eq(QuestionTagEntity::getQuestionId, id));

        if (request.tagIds() != null && !request.tagIds().isEmpty()) {
            for (Long tagId : request.tagIds()) {
                QuestionTagEntity qt = new QuestionTagEntity();
                qt.setQuestionId(id);
                qt.setTagId(tagId);
                questionTagMapper.insert(qt);
            }
        }

        return getQuestion(id);
    }

    @Override
    public void deleteQuestion(Long id) {
        QuestionEntity entity = questionMapper.selectById(id);
        if (entity == null || Boolean.TRUE.equals(entity.getDeleted())) {
            throw new NotFoundException(ErrorCode.QUESTION_NOT_FOUND);
        }
        entity.setDeleted(true);
        questionMapper.updateById(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public long countQuestions(QuestionListRequest request) {
        return questionMapper.countQuestionList(
                request.type(),
                request.difficulty(),
                request.tagIds(),
                request.keyword()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public long countSearch(String keyword) {
        return questionMapper.countSearchByVector(keyword);
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

    private AnswerVO toAnswerVO(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return new ObjectMapper().readValue(json, AnswerVO.class);
        } catch (Exception e) {
            return new AnswerVO(json, null, null);
        }
    }

    public QuestionDetailVO toDetailVO(QuestionEntity entity, List<com.prepdeck.question.domain.TagEntity> tags, boolean isFavorited) {
        AnswerVO answerVO = entity.getAnswer();

        List<com.prepdeck.question.dto.TagVO> tagVOs = tags.stream()
                .map(t -> new com.prepdeck.question.dto.TagVO(t.getId(), t.getName(), t.getDescription(), t.getCreateTime()))
                .toList();

        return new QuestionDetailVO(
                entity.getId(),
                entity.getTitle(),
                entity.getContent(),
                answerVO,
                entity.getType(),
                entity.getDifficulty(),
                entity.getEasyMistake(),
                entity.getInterviewCount(),
                entity.getViews(),
                entity.getFavorites(),
                tagVOs,
                isFavorited,
                entity.getCreateTime()
        );
    }
}
