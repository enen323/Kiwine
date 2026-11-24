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
import com.prepdeck.common.util.SecurityContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
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
class DailyChallengeServiceImplTest {

    @Mock private DailyChallengeMapper dailyChallengeMapper;
    @Mock private QuestionMapper questionMapper;
    @Mock private QuestionTagMapper questionTagMapper;
    @Mock private TagMapper tagMapper;
    @Mock private QuestionService questionService;

    private DailyChallengeServiceImpl dailyChallengeService;
    private MockedStatic<SecurityContext> securityContextMock;

    @BeforeEach
    void setUp() {
        dailyChallengeService = new DailyChallengeServiceImpl(dailyChallengeMapper, questionMapper, questionTagMapper, tagMapper, questionService);
        securityContextMock = mockStatic(SecurityContext.class);
        securityContextMock.when(SecurityContext::getCurrentUserId).thenReturn(1L);
    }

    @AfterEach
    void tearDown() {
        securityContextMock.close();
    }

    // ========== getTodayChallenge ==========

    // 测：今日已存在题目 → 返回已有记录
    @Test
    void getTodayChallenge_exists_returnsExisting() {
        DailyChallengeEntity exist = new DailyChallengeEntity();
        exist.setId(1L);
        exist.setUserId(1L);
        exist.setQuestionId(100L);
        exist.setChallengeDate(LocalDate.now());
        exist.setCompleted(false);

        QuestionEntity question = new QuestionEntity();
        question.setId(100L);
        question.setTitle("Daily Q");
        question.setType("Java");
        question.setDifficulty("简单");
        question.setInterviewCount(1);
        question.setFavorites(0);
        question.setViews(10);
        question.setDeleted(false);

        QuestionDetailVO questionVO = new QuestionDetailVO(
                100L, "Daily Q", "content", null, "Java", "简单", null, 1, 10, 0, List.of(), false, null);

        when(dailyChallengeMapper.findToday(eq(1L), any(java.time.LocalDate.class))).thenReturn(exist);
        when(questionMapper.selectById(100L)).thenReturn(question);
        when(questionTagMapper.findTagIdsByQuestionId(100L)).thenReturn(Collections.emptyList());
        when(questionService.toDetailVO(any(QuestionEntity.class), anyList(), eq(false))).thenReturn(questionVO);

        DailyChallengeVO vo = dailyChallengeService.getTodayChallenge();

        assertEquals(1L, vo.id());
        assertFalse(vo.completed());
        verify(dailyChallengeMapper, never()).upsert(any());
    }

    // 测：今日已完成题目 → 返回 completed=true
    @Test
    void getTodayChallenge_completed_returnsCompleted() {
        DailyChallengeEntity exist = new DailyChallengeEntity();
        exist.setId(1L);
        exist.setUserId(1L);
        exist.setQuestionId(100L);
        exist.setChallengeDate(LocalDate.now());
        exist.setCompleted(true);

        QuestionEntity question = new QuestionEntity();
        question.setId(100L);
        question.setTitle("Daily Q");
        question.setType("Java");
        question.setDifficulty("简单");
        question.setInterviewCount(1);
        question.setFavorites(0);
        question.setViews(10);
        question.setDeleted(false);

        QuestionDetailVO questionVO = new QuestionDetailVO(
                100L, "Daily Q", "content", null, "Java", "简单", null, 1, 10, 0, List.of(), false, null);

        when(dailyChallengeMapper.findToday(eq(1L), any(java.time.LocalDate.class))).thenReturn(exist);
        when(questionMapper.selectById(100L)).thenReturn(question);
        when(questionTagMapper.findTagIdsByQuestionId(100L)).thenReturn(Collections.emptyList());
        when(questionService.toDetailVO(any(QuestionEntity.class), anyList(), eq(false))).thenReturn(questionVO);

        DailyChallengeVO vo = dailyChallengeService.getTodayChallenge();

        assertTrue(vo.completed());
    }

    // 测：今日记录关联的题目已删除 → 抛 NotFoundException
    @Test
    void getTodayChallenge_questionDeleted_throwsNotFoundException() {
        DailyChallengeEntity exist = new DailyChallengeEntity();
        exist.setId(1L);
        exist.setUserId(1L);
        exist.setQuestionId(100L);
        exist.setChallengeDate(LocalDate.now());
        exist.setCompleted(false);

        QuestionEntity question = new QuestionEntity();
        question.setId(100L);
        question.setDeleted(true);

        when(dailyChallengeMapper.findToday(eq(1L), any(java.time.LocalDate.class))).thenReturn(exist);
        when(questionMapper.selectById(100L)).thenReturn(question);

        assertThrows(NotFoundException.class, () -> dailyChallengeService.getTodayChallenge());
    }

    // 测：今日无记录且有随机题目 → 创建新记录
    @Test
    void getTodayChallenge_noRecord_createsNew() {
        DailyChallengeEntity saved = new DailyChallengeEntity();
        saved.setId(5L);
        saved.setUserId(1L);
        saved.setQuestionId(200L);
        saved.setChallengeDate(LocalDate.now());
        saved.setCompleted(false);

        when(dailyChallengeMapper.findToday(eq(1L), any(java.time.LocalDate.class)))
                .thenReturn(null)
                .thenReturn(saved);

        List<QuestionEntity> randomQuestions = List.of(new QuestionEntity() {{
            setId(200L);
            setTitle("Random");
            setType("Java");
            setDifficulty("简单");
            setInterviewCount(1);
            setFavorites(0);
            setViews(10);
            setDeleted(false);
        }});
        when(questionMapper.selectRandom(10)).thenReturn(randomQuestions);

        doAnswer(inv -> {
            DailyChallengeEntity e = inv.getArgument(0);
            e.setId(5L);
            return 1;
        }).when(dailyChallengeMapper).upsert(any(DailyChallengeEntity.class));

        QuestionDetailVO questionVO = new QuestionDetailVO(
                200L, "Random", "content", null, "Java", "简单", null, 1, 10, 0, List.of(), false, null);

        when(questionTagMapper.findTagIdsByQuestionId(200L)).thenReturn(Collections.emptyList());
        when(questionService.toDetailVO(any(QuestionEntity.class), anyList(), eq(false))).thenReturn(questionVO);

        DailyChallengeVO vo = dailyChallengeService.getTodayChallenge();

        assertEquals(5L, vo.id());
        verify(dailyChallengeMapper).upsert(any(DailyChallengeEntity.class));
    }

    // 测：随机选题为空 → 抛 NotFoundException
    @Test
    void getTodayChallenge_noRandomQuestions_throwsNotFoundException() {
        when(dailyChallengeMapper.findToday(eq(1L), any(java.time.LocalDate.class))).thenReturn(null);
        when(questionMapper.selectRandom(10)).thenReturn(Collections.emptyList());

        assertThrows(NotFoundException.class, () -> dailyChallengeService.getTodayChallenge());
    }

    // ========== completeTodayChallenge ==========

    // 测：完成今日一题 → 返回 completed=true
    @Test
    void completeTodayChallenge_existing_marksCompleted() {
        DailyChallengeEntity exist = new DailyChallengeEntity();
        exist.setId(1L);
        exist.setUserId(1L);
        exist.setQuestionId(100L);
        exist.setChallengeDate(LocalDate.now());
        exist.setCompleted(false);

        QuestionEntity question = new QuestionEntity();
        question.setId(100L);
        question.setTitle("Daily Q");
        question.setType("Java");
        question.setDifficulty("简单");
        question.setInterviewCount(1);
        question.setFavorites(0);
        question.setViews(10);
        question.setDeleted(false);

        QuestionDetailVO questionVO = new QuestionDetailVO(
                100L, "Daily Q", "content", null, "Java", "简单", null, 1, 10, 0, List.of(), false, null);

        when(dailyChallengeMapper.findToday(eq(1L), any(java.time.LocalDate.class))).thenReturn(exist);
        when(questionMapper.selectById(100L)).thenReturn(question);
        when(questionTagMapper.findTagIdsByQuestionId(100L)).thenReturn(Collections.emptyList());
        when(questionService.toDetailVO(any(QuestionEntity.class), anyList(), eq(false))).thenReturn(questionVO);

        DailyChallengeVO vo = dailyChallengeService.completeTodayChallenge();

        assertTrue(vo.completed());
        verify(dailyChallengeMapper).updateById(exist);
    }

    // 测：今日无记录 → 自动创建并标记完成
    @Test
    void completeTodayChallenge_noRecord_createsAndCompletes() {
        DailyChallengeEntity saved = new DailyChallengeEntity();
        saved.setId(5L);
        saved.setUserId(1L);
        saved.setQuestionId(200L);
        saved.setChallengeDate(LocalDate.now());
        saved.setCompleted(false);

        QuestionEntity question = new QuestionEntity();
        question.setId(200L);
        question.setTitle("Random");
        question.setType("Java");
        question.setDifficulty("简单");
        question.setInterviewCount(1);
        question.setFavorites(0);
        question.setViews(10);
        question.setDeleted(false);

        QuestionDetailVO questionVO = new QuestionDetailVO(
                200L, "Random", "content", null, "Java", "简单", null, 1, 10, 0, List.of(), false, null);

        when(dailyChallengeMapper.findToday(eq(1L), any(java.time.LocalDate.class)))
                .thenReturn(null, null, saved);
        when(questionMapper.selectRandom(10)).thenReturn(List.of(question));
        when(questionMapper.selectById(200L)).thenReturn(question);

        doAnswer(inv -> {
            DailyChallengeEntity e = inv.getArgument(0);
            e.setId(5L);
            return 1;
        }).when(dailyChallengeMapper).upsert(any(DailyChallengeEntity.class));

        when(questionTagMapper.findTagIdsByQuestionId(200L)).thenReturn(Collections.emptyList());
        when(questionService.toDetailVO(any(QuestionEntity.class), anyList(), eq(false))).thenReturn(questionVO);

        DailyChallengeVO vo = dailyChallengeService.completeTodayChallenge();

        assertTrue(vo.completed());
        verify(dailyChallengeMapper).upsert(any(DailyChallengeEntity.class));
        verify(dailyChallengeMapper).updateById(any(DailyChallengeEntity.class));
    }

    // 测：已完成 → 幂等返回 completed=true
    @Test
    void completeTodayChallenge_alreadyCompleted_returnsCompleted() {
        DailyChallengeEntity exist = new DailyChallengeEntity();
        exist.setId(1L);
        exist.setUserId(1L);
        exist.setQuestionId(100L);
        exist.setChallengeDate(LocalDate.now());
        exist.setCompleted(true);

        QuestionEntity question = new QuestionEntity();
        question.setId(100L);
        question.setTitle("Daily Q");
        question.setType("Java");
        question.setDifficulty("简单");
        question.setInterviewCount(1);
        question.setFavorites(0);
        question.setViews(10);
        question.setDeleted(false);

        QuestionDetailVO questionVO = new QuestionDetailVO(
                100L, "Daily Q", "content", null, "Java", "简单", null, 1, 10, 0, List.of(), false, null);

        when(dailyChallengeMapper.findToday(eq(1L), any(java.time.LocalDate.class))).thenReturn(exist);
        when(questionMapper.selectById(100L)).thenReturn(question);
        when(questionTagMapper.findTagIdsByQuestionId(100L)).thenReturn(Collections.emptyList());
        when(questionService.toDetailVO(any(QuestionEntity.class), anyList(), eq(false))).thenReturn(questionVO);

        DailyChallengeVO vo = dailyChallengeService.completeTodayChallenge();

        assertTrue(vo.completed());
        verify(dailyChallengeMapper, never()).updateById(any(DailyChallengeEntity.class));
    }
}
