package com.prepdeck.question.controller;

import com.prepdeck.common.model.PageResult;
import com.prepdeck.common.model.Result;
import com.prepdeck.common.dto.UserPracticeSummaryVO;
import com.prepdeck.question.dto.*;
import com.prepdeck.question.service.DailyChallengeService;
import com.prepdeck.question.service.FavoriteService;
import com.prepdeck.question.service.QuestionService;
import com.prepdeck.question.service.TagService;
import com.prepdeck.question.service.RecommendationService;
import com.prepdeck.question.service.UserPracticeRecordService;
import com.prepdeck.question.client.InterviewClient;
import com.prepdeck.common.util.SecurityContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class QuestionController {

    private final QuestionService questionService;
    private final TagService tagService;
    private final FavoriteService favoriteService;
    private final DailyChallengeService dailyChallengeService;
    private final RecommendationService recommendationService;
    private final UserPracticeRecordService userPracticeRecordService;
    private final InterviewClient interviewClient;

    // ==================== 题目 ====================

    @GetMapping("/questions")
    public Result<PageResult<QuestionDetailVO>> listQuestions(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String order,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) List<Long> tagIds,
            @RequestParam(required = false) String keyword
    ) {
        QuestionListRequest request = new QuestionListRequest(page, pageSize, sortBy, order, type, difficulty, tagIds, keyword);
        long total = questionService.countQuestions(request);
        List<QuestionDetailVO> data = questionService.listQuestions(request, total);
        return Result.success(PageResult.success(data, page != null ? page : 1, pageSize != null ? pageSize : 20, total));
    }

    @GetMapping("/questions/search")
    public Result<PageResult<QuestionDetailVO>> searchQuestions(
            @RequestParam String keyword,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize
    ) {
        long total = questionService.countSearch(keyword);
        List<QuestionDetailVO> data = questionService.searchQuestions(keyword, page, pageSize, total);
        return Result.success(PageResult.success(data, page, pageSize, total));
    }

    @GetMapping("/questions/{id}")
    public Result<QuestionDetailVO> getQuestion(@PathVariable Long id) {
        return Result.success(questionService.getQuestion(id));
    }

    @GetMapping("/questions/daily")
    public Result<DailyChallengeVO> getDailyChallenge() {
        return Result.success(dailyChallengeService.getTodayChallenge());
    }

    @PostMapping("/questions/daily/complete")
    public Result<DailyChallengeVO> completeDailyChallenge() {
        return Result.success(dailyChallengeService.completeTodayChallenge());
    }

    // ==================== 推荐 ====================

    @GetMapping("/questions/recommendations")
    public Result<RecommendationVO> getRecommendations(
            @RequestParam(required = false, defaultValue = "10") Integer count) {
        Long userId = SecurityContext.getCurrentUserId();

        UserPracticeSummaryVO summary = interviewClient.getUserPracticeSummary(userId, 5, 50);
        var request = new com.prepdeck.question.dto.RecommendationRequest(count);
        RecommendationVO result = recommendationService.getRecommendations(request, summary);
        return Result.success(result);
    }

    @PostMapping("/questions/{id}/practice")
    public Result<Void> recordPractice(@PathVariable Long id) {
        Long userId = SecurityContext.getCurrentUserId();
        userPracticeRecordService.recordPractice(userId, id, "MANUAL", null);
        return Result.success();
    }

    // ==================== 收藏 ====================

    @PostMapping("/questions/favorites")
    public Result<Void> favorite(@RequestBody FavoriteRequest request) {
        favoriteService.favorite(request.questionId());
        return Result.success();
    }

    @DeleteMapping("/questions/favorites/{questionId}")
    public Result<Void> unfavorite(@PathVariable Long questionId) {
        favoriteService.unfavorite(questionId);
        return Result.success();
    }

    @GetMapping("/questions/favorites")
    public Result<PageResult<QuestionDetailVO>> listFavorites(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize
    ) {
        Long userId = SecurityContext.getCurrentUserId();
        long total = favoriteService.countFavorites(userId);
        List<QuestionDetailVO> data = favoriteService.listFavorites(page, pageSize);
        return Result.success(PageResult.success(data, page, pageSize, total));
    }

    // ==================== 标签 ====================

    @GetMapping("/tags")
    public Result<List<TagVO>> listTags() {
        return Result.success(tagService.listTags());
    }

    @PostMapping("/tags")
    public Result<TagVO> createTag(@RequestBody CreateTagRequest request) {
        return Result.success(tagService.createTag(request));
    }

    @PutMapping("/tags/{id}")
    public Result<TagVO> updateTag(@PathVariable Long id, @RequestBody UpdateTagRequest request) {
        return Result.success(tagService.updateTag(id, request));
    }

    @DeleteMapping("/tags/{id}")
    public Result<Void> deleteTag(@PathVariable Long id) {
        tagService.deleteTag(id);
        return Result.success();
    }
}
