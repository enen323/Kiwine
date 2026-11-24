package com.prepdeck.question.controller;

import com.prepdeck.common.model.PageResult;
import com.prepdeck.common.model.Result;
import com.prepdeck.common.util.SecurityContext;
import com.prepdeck.question.dto.*;
import com.prepdeck.question.service.DailyChallengeService;
import com.prepdeck.question.service.FavoriteService;
import com.prepdeck.question.service.QuestionService;
import com.prepdeck.question.service.TagService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuestionControllerTest {

    @Mock private QuestionService questionService;
    @Mock private TagService tagService;
    @Mock private FavoriteService favoriteService;
    @Mock private DailyChallengeService dailyChallengeService;

    @InjectMocks
    private QuestionController questionController;

    private MockedStatic<SecurityContext> securityContextMock;

    // ========== 题目列表 ==========

    // 测：查询题目列表 → 返回分页结果
    @Test
    void listQuestions_returnsPagedResult() {
        when(questionService.countQuestions(any())).thenReturn(2L);
        when(questionService.listQuestions(any(), anyLong())).thenReturn(List.of(
                new QuestionDetailVO(1L, "Java", "content",
                        new AnswerVO("s", "a", "c"), "Java", "简单", "mistake", 1, 10, 5,
                        List.of(), false, null)
        ));

        Result<PageResult<QuestionDetailVO>> result = questionController.listQuestions(
                1, 20, "heat", "desc", "Java", "简单", List.of(), null);

        assertEquals(0, result.code());
        assertNotNull(result.data());
        assertEquals(1, result.data().data().size());
        verify(questionService).countQuestions(any());
        verify(questionService).listQuestions(any(), eq(2L));
    }

    // 测：查询题目列表默认分页
    @Test
    void listQuestions_defaultPagination() {
        when(questionService.countQuestions(any())).thenReturn(0L);
        when(questionService.listQuestions(any(), anyLong())).thenReturn(Collections.emptyList());

        Result<PageResult<QuestionDetailVO>> result = questionController.listQuestions(
                null, null, null, null, null, null, null, null);

        assertEquals(0, result.code());
        assertEquals(1, result.data().page());
        assertEquals(20, result.data().pageSize());
    }

    // ========== 搜索题目 ==========

    // 测：搜索题目 → 返回结果
    @Test
    void searchQuestions_returnsResults() {
        when(questionService.countSearch("Java")).thenReturn(1L);
        when(questionService.searchQuestions(any(), anyInt(), anyInt(), anyLong())).thenReturn(List.of(
                new QuestionDetailVO(1L, "Java", "content",
                        new AnswerVO("s", "a", "c"), "Java", "简单", "mistake", 1, 10, 5,
                        List.of(), false, null)
        ));

        Result<PageResult<QuestionDetailVO>> result = questionController.searchQuestions("Java", 1, 20);

        assertEquals(0, result.code());
        assertEquals(1, result.data().data().size());
    }

    // ========== 题目详情 ==========

    // 测：获取题目详情
    @Test
    void getQuestion_returnsDetail() {
        when(questionService.getQuestion(1L)).thenReturn(
                new QuestionDetailVO(1L, "Java", "content",
                        new AnswerVO("s", "a", "c"), "Java", "简单", "mistake", 1, 10, 5,
                        List.of(), false, null)
        );

        Result<QuestionDetailVO> result = questionController.getQuestion(1L);

        assertEquals(0, result.code());
        assertNotNull(result.data());
    }

    // 测：题目不存在 → 抛异常（全局处理器捕获）
    @Test
    void getQuestion_notFound_throwsException() {
        when(questionService.getQuestion(999L)).thenThrow(
                new com.prepdeck.common.exception.NotFoundException(com.prepdeck.common.enums.ErrorCode.QUESTION_NOT_FOUND)
        );

        assertThrows(com.prepdeck.common.exception.NotFoundException.class,
                () -> questionController.getQuestion(999L));
    }

    // ========== 每日一题 ==========

    // 测：获取今日一题
    @Test
    void getDailyChallenge_returnsChallenge() {
        when(dailyChallengeService.getTodayChallenge()).thenReturn(
                new DailyChallengeVO(1L,
                        new QuestionDetailVO(1L, "Java", "content",
                                new AnswerVO("s", "a", "c"), "Java", "简单", "mistake", 1, 10, 5,
                                List.of(), false, null),
                        false)
        );

        Result<DailyChallengeVO> result = questionController.getDailyChallenge();

        assertEquals(0, result.code());
        assertFalse(result.data().completed());
    }

    // 测：完成今日一题
    @Test
    void completeDailyChallenge_returnsCompletedChallenge() {
        when(dailyChallengeService.completeTodayChallenge()).thenReturn(
                new DailyChallengeVO(1L,
                        new QuestionDetailVO(1L, "Java", "content",
                                new AnswerVO("s", "a", "c"), "Java", "简单", "mistake", 1, 10, 5,
                                List.of(), false, null),
                        true)
        );

        Result<DailyChallengeVO> result = questionController.completeDailyChallenge();

        assertEquals(0, result.code());
        assertTrue(result.data().completed());
        verify(dailyChallengeService).completeTodayChallenge();
    }

    // ========== 收藏 ==========

    // 测：收藏题目
    @Test
    void favorite_returnsSuccess() {
        doNothing().when(favoriteService).favorite(1L);

        Result<Void> result = questionController.favorite(new FavoriteRequest(1L));

        assertEquals(0, result.code());
        verify(favoriteService).favorite(1L);
    }

    // 测：取消收藏
    @Test
    void unfavorite_returnsSuccess() {
        doNothing().when(favoriteService).unfavorite(1L);

        Result<Void> result = questionController.unfavorite(1L);

        assertEquals(0, result.code());
        verify(favoriteService).unfavorite(1L);
    }

    // 测：收藏列表
    @Test
    void listFavorites_returnsPagedList() {
        try (MockedStatic<SecurityContext> mocked = mockStatic(SecurityContext.class)) {
            mocked.when(SecurityContext::getCurrentUserId).thenReturn(1L);
            when(favoriteService.countFavorites(anyLong())).thenReturn(10L);
            when(favoriteService.listFavorites(1, 20)).thenReturn(List.of(
                    new QuestionDetailVO(1L, "Java", "content",
                            new AnswerVO("s", "a", "c"), "Java", "简单", "mistake", 1, 10, 5,
                            List.of(), true, null)
            ));

            Result<PageResult<QuestionDetailVO>> result = questionController.listFavorites(1, 20);

            assertEquals(0, result.code());
            assertEquals(1, result.data().data().size());
            assertEquals(10, result.data().total());
            verify(favoriteService).countFavorites(anyLong());
        }
    }

    // ========== 标签 ==========

    // 测：查询标签列表
    @Test
    void listTags_returnsTagList() {
        when(tagService.listTags()).thenReturn(List.of(
                new TagVO(1L, "Java", "desc", null)
        ));

        Result<List<TagVO>> result = questionController.listTags();

        assertEquals(0, result.code());
        assertEquals(1, result.data().size());
    }

    // 测：创建标签
    @Test
    void createTag_returnsCreatedTag() {
        when(tagService.createTag(any())).thenReturn(
                new TagVO(10L, "NewTag", "desc", null)
        );

        Result<TagVO> result = questionController.createTag(new CreateTagRequest("NewTag", "desc"));

        assertEquals(0, result.code());
        assertEquals("NewTag", result.data().name());
    }

    // 测：更新标签
    @Test
    void updateTag_returnsUpdatedTag() {
        when(tagService.updateTag(anyLong(), any())).thenReturn(
                new TagVO(1L, "Updated", "new desc", null)
        );

        Result<TagVO> result = questionController.updateTag(1L, new UpdateTagRequest("Updated", "new desc"));

        assertEquals(0, result.code());
        assertEquals("Updated", result.data().name());
    }

    // 测：删除标签
    @Test
    void deleteTag_returnsSuccess() {
        doNothing().when(tagService).deleteTag(1L);

        Result<Void> result = questionController.deleteTag(1L);

        assertEquals(0, result.code());
        verify(tagService).deleteTag(1L);
    }
}
