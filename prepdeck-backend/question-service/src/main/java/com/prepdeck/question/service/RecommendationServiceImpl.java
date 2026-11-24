package com.prepdeck.question.service;

import com.prepdeck.common.dto.UserInterviewScoreVO;
import com.prepdeck.common.dto.UserPracticeSummaryVO;
import com.prepdeck.question.domain.QuestionEntity;
import com.prepdeck.question.domain.UserPracticeRecordEntity;
import com.prepdeck.question.dto.QuestionDetailVO;
import com.prepdeck.question.dto.RecommendationRequest;
import com.prepdeck.question.dto.RecommendationVO;
import com.prepdeck.question.repository.QuestionMapper;
import com.prepdeck.question.repository.QuestionTagMapper;
import com.prepdeck.question.repository.TagMapper;
import com.prepdeck.question.repository.UserPracticeRecordMapper;
import com.prepdeck.common.enums.ErrorCode;
import com.prepdeck.common.exception.BadRequestException;
import com.prepdeck.common.util.SecurityContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class RecommendationServiceImpl implements RecommendationService {

    private static final String REDIS_KEY_PREFIX = "question:recommendation:";
    private static final long CACHE_TTL_MINUTES = 5;

    private static final Map<String, List<String>> DIMENSION_QUESTION_TYPES = Map.of(
            "scoreTech", List.of("TECH_BASIC", "ALGORITHM", "SYSTEM_DESIGN", "TECH_PROJECT"),
            "scoreExpression", List.of("SELF_INTRO", "BEHAVIORAL"),
            "scoreProject", List.of("TECH_PROJECT"),
            "scoreLogic", List.of("TECH_BASIC", "ALGORITHM", "SYSTEM_DESIGN"),
            "scoreAdaptability", List.of("BEHAVIORAL", "REVERSE_QA")
    );

    private static final int WEAK_THRESHOLD = 70;
    private static final int MAX_QUESTION_TYPES = 8;

    private final QuestionMapper questionMapper;
    private final QuestionTagMapper questionTagMapper;
    private final TagMapper tagMapper;
    private final QuestionService questionService;
    private final UserPracticeRecordService userPracticeRecordService;
    private final UserPracticeRecordMapper userPracticeRecordMapper;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    @Transactional(readOnly = true)
    public RecommendationVO getRecommendations(RecommendationRequest request, UserPracticeSummaryVO summary) {
        Long userId = SecurityContext.getCurrentUserId();
        String cacheKey = REDIS_KEY_PREFIX + userId;

        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                return new com.fasterxml.jackson.databind.ObjectMapper()
                        .readValue(cached, RecommendationVO.class);
            } catch (Exception e) {
                stringRedisTemplate.delete(cacheKey);
            }
        }

        RecommendationVO result = computeRecommendations(request, summary, userId);

        try {
            stringRedisTemplate.opsForValue().set(
                    cacheKey,
                    new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(result),
                    CACHE_TTL_MINUTES,
                    TimeUnit.MINUTES
            );
        } catch (Exception e) {
            log.debug("缓存推荐结果失败: userId={}", userId, e);
        }

        return result;
    }

    private RecommendationVO computeRecommendations(RecommendationRequest request,
                                                     UserPracticeSummaryVO summary,
                                                     Long userId) {
        int count = request.count() != null ? Math.min(request.count(), 50) : 10;

        if (summary.recentScores() == null || summary.recentScores().isEmpty()) {
            return hotRecommendation(userId, count);
        }

        Set<String> weakTypes = findWeakQuestionTypes(summary.recentScores());
        Set<String> practicedDescs = summary.recentPracticedDescriptions() == null
                ? Set.of()
                : new HashSet<>(summary.recentPracticedDescriptions());

        if (weakTypes.isEmpty()) {
            return hotRecommendation(userId, count);
        }

        List<String> typeList = weakTypes.stream().limit(MAX_QUESTION_TYPES).toList();

        List<QuestionEntity> entities = questionMapper.selectRecommendationList(
                typeList,
                new ArrayList<>(practicedDescs),
                "interviewCount",
                "DESC",
                count * 3,
                0
        );

        List<QuestionEntity> filtered = entities.stream()
                .filter(q -> !Boolean.TRUE.equals(q.getDeleted()))
                .filter(q -> {
                    String desc = q.getContent();
                    return desc == null || desc.isBlank() || !practicedDescs.contains(desc);
                })
                .limit(count)
                .toList();

        if (filtered.isEmpty()) {
            return hotRecommendation(userId, count);
        }

        List<QuestionDetailVO> questionVOs = buildQuestionDetailVOs(filtered, userId);

        List<String> weakDimensions = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : DIMENSION_QUESTION_TYPES.entrySet()) {
            boolean isWeak = summary.recentScores().stream()
                    .anyMatch(s -> {
                        Integer score = switch (entry.getKey()) {
                            case "scoreTech" -> s.scoreTech();
                            case "scoreExpression" -> s.scoreExpression();
                            case "scoreProject" -> s.scoreProject();
                            case "scoreLogic" -> s.scoreLogic();
                            case "scoreAdaptability" -> s.scoreAdaptability();
                            default -> null;
                        };
                        return score != null && score < WEAK_THRESHOLD;
                    });
            if (isWeak) {
                weakDimensions.add(entry.getKey());
            }
        }

        String message = "基于你最近" + summary.recentScores().size() + "次面试，"
                + String.join("、", weakDimensions) + "维度较弱，推荐针对性练习";

        return new RecommendationVO(
                RecommendationVO.RecommendationStrategy.WEAK_AREA,
                message,
                questionVOs
        );
    }

    private Set<String> findWeakQuestionTypes(List<UserInterviewScoreVO> scores) {
        Map<String, List<Integer>> dimScores = new HashMap<>();
        for (UserInterviewScoreVO score : scores) {
            dimScores.computeIfAbsent("scoreTech", k -> new ArrayList<>()).add(score.scoreTech());
            dimScores.computeIfAbsent("scoreExpression", k -> new ArrayList<>()).add(score.scoreExpression());
            dimScores.computeIfAbsent("scoreProject", k -> new ArrayList<>()).add(score.scoreProject());
            dimScores.computeIfAbsent("scoreLogic", k -> new ArrayList<>()).add(score.scoreLogic());
            dimScores.computeIfAbsent("scoreAdaptability", k -> new ArrayList<>()).add(score.scoreAdaptability());
        }

        Set<String> weakTypes = new LinkedHashSet<>();
        for (Map.Entry<String, List<Integer>> entry : dimScores.entrySet()) {
            double avg = entry.getValue().stream()
                    .filter(Objects::nonNull)
                    .mapToInt(Integer::intValue)
                    .average()
                    .orElse(100);
            if (avg < WEAK_THRESHOLD) {
                weakTypes.addAll(DIMENSION_QUESTION_TYPES.getOrDefault(entry.getKey(), List.of()));
            }
        }
        return weakTypes;
    }

    private RecommendationVO hotRecommendation(Long userId, int count) {
        List<QuestionEntity> entities = questionMapper.selectRecommendationList(
                null, null, "interviewCount", "DESC", count, 0
        );

        List<QuestionEntity> filtered = entities.stream()
                .filter(q -> !Boolean.TRUE.equals(q.getDeleted()))
                .toList();

        List<QuestionDetailVO> questionVOs = buildQuestionDetailVOs(filtered, userId);

        return new RecommendationVO(
                RecommendationVO.RecommendationStrategy.HOT,
                "热门题目推荐",
                questionVOs
        );
    }

    private List<QuestionDetailVO> buildQuestionDetailVOs(List<QuestionEntity> entities, Long userId) {
        if (entities.isEmpty()) {
            return List.of();
        }

        List<Long> questionIds = entities.stream()
                .map(QuestionEntity::getId)
                .toList();

        List<com.prepdeck.question.domain.QuestionTagEntity> questionTags =
                questionTagMapper.findTagIdsByQuestionIds(questionIds);
        Map<Long, List<Long>> questionTagMap = questionTags.stream()
                .collect(Collectors.groupingBy(com.prepdeck.question.domain.QuestionTagEntity::getQuestionId,
                        Collectors.mapping(com.prepdeck.question.domain.QuestionTagEntity::getTagId, Collectors.toList())));

        Map<Long, List<com.prepdeck.question.domain.TagEntity>> tagMap = fetchTags(questionTagMap);

        Map<Long, UserPracticeRecordEntity> practiceMap = userPracticeRecordMapper
                .selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserPracticeRecordEntity>()
                        .eq(UserPracticeRecordEntity::getUserId, userId)
                        .eq(UserPracticeRecordEntity::getDeleted, false)
                        .in(UserPracticeRecordEntity::getQuestionId, questionIds))
                .stream()
                .collect(Collectors.toMap(UserPracticeRecordEntity::getQuestionId, p -> p));

        return entities.stream()
                .map(q -> {
                    List<com.prepdeck.question.domain.TagEntity> tags =
                            tagMap.getOrDefault(q.getId(), List.of());
                    boolean isFavorited = practiceMap.containsKey(q.getId());
                    return questionService.toDetailVO(q, tags, isFavorited);
                })
                .toList();
    }

    private Map<Long, List<com.prepdeck.question.domain.TagEntity>> fetchTags(
            Map<Long, List<Long>> questionTagMap) {
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
                                .filter(Objects::nonNull)
                                .toList()
                ));
    }
}
