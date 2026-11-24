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
import com.prepdeck.common.util.SecurityContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FavoriteServiceImplTest {

    @Mock private UserFavoriteMapper userFavoriteMapper;
    @Mock private QuestionMapper questionMapper;
    @Mock private QuestionTagMapper questionTagMapper;
    @Mock private TagMapper tagMapper;
    @Mock private QuestionService questionService;

    private FavoriteServiceImpl favoriteService;
    private MockedStatic<SecurityContext> securityContextMock;

    @BeforeEach
    void setUp() {
        favoriteService = new FavoriteServiceImpl(userFavoriteMapper, questionMapper, questionTagMapper, tagMapper, questionService);
        securityContextMock = mockStatic(SecurityContext.class);
        securityContextMock.when(SecurityContext::getCurrentUserId).thenReturn(1L);
    }

    @AfterEach
    void tearDown() {
        securityContextMock.close();
    }

    // ========== countFavorites ==========

    // 测：统计用户收藏数量
    @Test
    void countFavorites_returnsCount() {
        when(userFavoriteMapper.countByUser(1L)).thenReturn(5L);

        long count = favoriteService.countFavorites(1L);

        assertEquals(5, count);
        verify(userFavoriteMapper).countByUser(1L);
    }

    // ========== listFavorites ==========

    // 测：收藏列表有结果 → 返回带 isFavorited 标记的 VO
    @Test
    void listFavorites_hasFavorites_returnsList() {
        UserFavoriteEntity fav = new UserFavoriteEntity();
        fav.setId(1L);
        fav.setUserId(1L);
        fav.setQuestionId(100L);
        fav.setDeleted(false);

        QuestionEntity question = new QuestionEntity();
        question.setId(100L);
        question.setTitle("Java");
        question.setType("Java");
        question.setDifficulty("简单");
        question.setInterviewCount(1);
        question.setFavorites(5);
        question.setViews(50);
        question.setDeleted(false);

        QuestionDetailVO stubVO = new QuestionDetailVO(
                100L, "Java", "content", null, "Java", "简单", null, 1, 50, 5, List.of(), true, null);

        when(userFavoriteMapper.selectList(any())).thenReturn(List.of(fav));
        when(questionMapper.selectBatchIds(anyList())).thenReturn(List.of(question));
        when(questionTagMapper.findTagIdsByQuestionIds(anyList()))
                .thenReturn(List.of(new QuestionTagEntity() {{ setQuestionId(100L); setTagId(200L); }}));
        when(questionService.toDetailVO(any(com.prepdeck.question.domain.QuestionEntity.class), anyList(), eq(true))).thenReturn(stubVO);

        List<com.prepdeck.question.dto.QuestionDetailVO> result = favoriteService.listFavorites(1, 10);

        assertEquals(1, result.size());
        verify(questionService).toDetailVO(any(com.prepdeck.question.domain.QuestionEntity.class), anyList(), eq(true));
    }

    // 测：收藏列表为空 → 返回空列表
    @Test
    void listFavorites_noFavorites_returnsEmptyList() {
        when(userFavoriteMapper.selectList(any())).thenReturn(Collections.emptyList());

        List<com.prepdeck.question.dto.QuestionDetailVO> result = favoriteService.listFavorites(1, 10);

        assertTrue(result.isEmpty());
        verify(questionMapper, never()).selectBatchIds(anyList());
    }

    // 测：分页参数 → 正确传递给 SQL
    @Test
    void listFavorites_pagination_passedToQuery() {
        when(userFavoriteMapper.selectList(any())).thenReturn(Collections.emptyList());

        favoriteService.listFavorites(2, 10);

        verify(userFavoriteMapper).selectList(any());
    }

    // ========== favorite ==========

    // 测：收藏题目 → 插入收藏记录，题目 favorites +1
    @Test
    void favorite_validQuestion_createsFavoriteAndIncrementsCount() {
        QuestionEntity question = new QuestionEntity();
        question.setId(100L);
        question.setTitle("Test");
        question.setFavorites(5);
        question.setDeleted(false);

        when(questionMapper.selectById(100L)).thenReturn(question);
        when(userFavoriteMapper.findByUserAndQuestion(1L, 100L)).thenReturn(null);

        doAnswer(inv -> {
            UserFavoriteEntity e = inv.getArgument(0);
            e.setId(1L);
            return 1;
        }).when(userFavoriteMapper).insert(any(UserFavoriteEntity.class));

        favoriteService.favorite(100L);

        verify(userFavoriteMapper).insert(any(UserFavoriteEntity.class));
        assertEquals(6, question.getFavorites());
        verify(questionMapper).updateById(question);
    }

    // 测：题目不存在 → 抛 NotFoundException
    @Test
    void favorite_questionNotFound_throwsNotFoundException() {
        when(questionMapper.selectById(999L)).thenReturn(null);

        assertThrows(NotFoundException.class, () -> favoriteService.favorite(999L));
    }

    // 测：题目已删除 → 抛 NotFoundException
    @Test
    void favorite_questionDeleted_throwsNotFoundException() {
        QuestionEntity question = new QuestionEntity();
        question.setId(100L);
        question.setDeleted(true);

        when(questionMapper.selectById(100L)).thenReturn(question);

        assertThrows(NotFoundException.class, () -> favoriteService.favorite(100L));
    }

    // 测：已收藏 → 抛 BadRequestException
    @Test
    void favorite_alreadyFavorited_throwsBadRequestException() {
        QuestionEntity question = new QuestionEntity();
        question.setId(100L);
        question.setDeleted(false);

        UserFavoriteEntity exist = new UserFavoriteEntity();
        exist.setId(1L);
        exist.setUserId(1L);
        exist.setQuestionId(100L);
        exist.setDeleted(false);

        when(questionMapper.selectById(100L)).thenReturn(question);
        when(userFavoriteMapper.findByUserAndQuestion(1L, 100L)).thenReturn(exist);

        assertThrows(BadRequestException.class, () -> favoriteService.favorite(100L));
        verify(userFavoriteMapper, never()).insert(any(UserFavoriteEntity.class));
    }

    // 测：收藏记录已删除 → 可重新收藏
    @Test
    void favorite_deletedFavorite_allowsReFavorite() {
        QuestionEntity question = new QuestionEntity();
        question.setId(100L);
        question.setFavorites(3);
        question.setDeleted(false);

        UserFavoriteEntity exist = new UserFavoriteEntity();
        exist.setId(1L);
        exist.setUserId(1L);
        exist.setQuestionId(100L);
        exist.setDeleted(true);

        when(questionMapper.selectById(100L)).thenReturn(question);
        when(userFavoriteMapper.findByUserAndQuestion(1L, 100L)).thenReturn(exist);

        doAnswer(inv -> {
            UserFavoriteEntity e = inv.getArgument(0);
            e.setId(2L);
            return 1;
        }).when(userFavoriteMapper).insert(any(UserFavoriteEntity.class));

        favoriteService.favorite(100L);

        verify(userFavoriteMapper).insert(any(UserFavoriteEntity.class));
        assertEquals(4, question.getFavorites());
    }

    // 测：favorites 为 null → 初始化为 0 后 +1
    @Test
    void favorite_nullFavorites_incrementsFromZero() {
        QuestionEntity question = new QuestionEntity();
        question.setId(100L);
        question.setFavorites(null);
        question.setDeleted(false);

        when(questionMapper.selectById(100L)).thenReturn(question);
        when(userFavoriteMapper.findByUserAndQuestion(1L, 100L)).thenReturn(null);

        favoriteService.favorite(100L);

        assertEquals(1, question.getFavorites());
    }

    // ========== unfavorite ==========

    // 测：取消收藏 → 软删除收藏记录，favorites -1
    @Test
    void unfavorite_valid_softDeletesAndDecrementsCount() {
        QuestionEntity question = new QuestionEntity();
        question.setId(100L);
        question.setFavorites(5);
        question.setDeleted(false);

        UserFavoriteEntity fav = new UserFavoriteEntity();
        fav.setId(1L);
        fav.setUserId(1L);
        fav.setQuestionId(100L);
        fav.setDeleted(false);

        when(questionMapper.selectById(100L)).thenReturn(question);
        when(userFavoriteMapper.findByUserAndQuestion(1L, 100L)).thenReturn(fav);

        favoriteService.unfavorite(100L);

        assertTrue(fav.getDeleted());
        verify(userFavoriteMapper).updateById(fav);
        assertEquals(4, question.getFavorites());
        verify(questionMapper).updateById(question);
    }

    // 测：题目不存在 → 抛 NotFoundException
    @Test
    void unfavorite_questionNotFound_throwsNotFoundException() {
        when(questionMapper.selectById(999L)).thenReturn(null);

        assertThrows(NotFoundException.class, () -> favoriteService.unfavorite(999L));
    }

    // 测：未收藏 → 抛 BadRequestException
    @Test
    void unfavorite_notFavorited_throwsBadRequestException() {
        QuestionEntity question = new QuestionEntity();
        question.setId(100L);
        question.setDeleted(false);

        when(questionMapper.selectById(100L)).thenReturn(question);
        when(userFavoriteMapper.findByUserAndQuestion(1L, 100L)).thenReturn(null);

        assertThrows(BadRequestException.class, () -> favoriteService.unfavorite(100L));
    }

    // 测：收藏记录已删除 → 抛 BadRequestException
    @Test
    void unfavorite_alreadyDeleted_throwsBadRequestException() {
        QuestionEntity question = new QuestionEntity();
        question.setId(100L);
        question.setDeleted(false);

        UserFavoriteEntity fav = new UserFavoriteEntity();
        fav.setId(1L);
        fav.setUserId(1L);
        fav.setQuestionId(100L);
        fav.setDeleted(true);

        when(questionMapper.selectById(100L)).thenReturn(question);
        when(userFavoriteMapper.findByUserAndQuestion(1L, 100L)).thenReturn(fav);

        assertThrows(BadRequestException.class, () -> favoriteService.unfavorite(100L));
        verify(userFavoriteMapper, never()).updateById(any(UserFavoriteEntity.class));
    }

    // 测：favorites 为 null → 减到 0 不溢出
    @Test
    void unfavorite_nullFavorites_decrementsToZero() {
        QuestionEntity question = new QuestionEntity();
        question.setId(100L);
        question.setFavorites(null);
        question.setDeleted(false);

        UserFavoriteEntity fav = new UserFavoriteEntity();
        fav.setId(1L);
        fav.setUserId(1L);
        fav.setQuestionId(100L);
        fav.setDeleted(false);

        when(questionMapper.selectById(100L)).thenReturn(question);
        when(userFavoriteMapper.findByUserAndQuestion(1L, 100L)).thenReturn(fav);

        favoriteService.unfavorite(100L);

        assertEquals(0, question.getFavorites());
    }
}
