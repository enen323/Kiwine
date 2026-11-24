package com.prepdeck.question.service;

import com.prepdeck.question.dto.AnswerVO;
import com.prepdeck.question.dto.CreateQuestionRequest;
import com.prepdeck.question.dto.QuestionDetailVO;
import com.prepdeck.question.dto.QuestionListRequest;
import com.prepdeck.question.dto.UpdateQuestionRequest;
import com.prepdeck.question.repository.QuestionMapper;
import com.prepdeck.question.repository.QuestionTagMapper;
import com.prepdeck.question.repository.TagMapper;
import com.prepdeck.question.repository.UserFavoriteMapper;
import com.prepdeck.question.domain.QuestionEntity;
import com.prepdeck.question.domain.QuestionTagEntity;
import com.prepdeck.common.enums.ErrorCode;
import com.prepdeck.common.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuestionServiceImplTest {

    @Mock private QuestionMapper questionMapper;
    @Mock private QuestionTagMapper questionTagMapper;
    @Mock private TagMapper tagMapper;
    @Mock private UserFavoriteMapper userFavoriteMapper;

    private QuestionServiceImpl questionService;

    @BeforeEach
    void setUp() {
        questionService = new QuestionServiceImpl(questionMapper, questionTagMapper, tagMapper, userFavoriteMapper);
    }

    // ========== listQuestions ==========

    // 测：列表查询有结果 → 返回带标签的 VO 列表
    @Test
    void listQuestions_hasResults_returnsVOList() {
        QuestionEntity entity = new QuestionEntity();
        entity.setId(1L);
        entity.setTitle("Java 基础");
        entity.setType("Java");
        entity.setDifficulty("简单");
        entity.setInterviewCount(5);
        entity.setFavorites(10);
        entity.setViews(100);
        entity.setDeleted(false);

        when(questionMapper.selectQuestionList(any(), any(), any(), any(), any(), any(), anyInt(), anyLong()))
                .thenReturn(List.of(entity));
        when(questionTagMapper.findTagIdsByQuestionIds(anyList()))
                .thenReturn(List.of(new QuestionTagEntity() {{ setQuestionId(1L); setTagId(10L); }}));
        when(tagMapper.selectBatchIds(anyList())).thenReturn(List.of());

        List<QuestionDetailVO> result = questionService.listQuestions(
                new QuestionListRequest(1, 20, "heat", "desc", "Java", "简单", List.of(), null), 1);

        assertEquals(1, result.size());
        assertEquals("Java 基础", result.getFirst().title());
        verify(questionTagMapper).findTagIdsByQuestionIds(anyList());
    }

    // 测：列表查询无结果 → 返回空列表，不触发标签查询 SQL 语法错误
    @Test
    void listQuestions_emptyResult_returnsEmptyList() {
        when(questionMapper.selectQuestionList(any(), any(), any(), any(), any(), any(), anyInt(), anyLong()))
                .thenReturn(Collections.emptyList());

        List<QuestionDetailVO> result = questionService.listQuestions(
                new QuestionListRequest(1, 20, null, null, null, null, List.of(), null), 0);

        assertTrue(result.isEmpty());
        verify(questionTagMapper, never()).findTagIdsByQuestionIds(anyList());
    }

    // 测：分页参数默认值 → page 为 null 时默认 1，pageSize 为 null 时默认 20
    @Test
    void listQuestions_nullPagination_usesDefaults() {
        when(questionMapper.selectQuestionList(any(), any(), any(), any(), any(), any(), anyInt(), anyLong()))
                .thenReturn(Collections.emptyList());

        questionService.listQuestions(
                new QuestionListRequest(null, null, null, null, null, null, List.of(), null), 0);

        ArgumentCaptor<Integer> limitCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Long> offsetCaptor = ArgumentCaptor.forClass(Long.class);
        verify(questionMapper).selectQuestionList(any(), any(), any(), any(), any(), any(), limitCaptor.capture(), offsetCaptor.capture());
        assertEquals(20, limitCaptor.getValue());
        assertEquals(0, offsetCaptor.getValue());
    }

    // ========== searchQuestions ==========

    // 测：全文检索有结果 → 返回 VO 列表
    @Test
    void searchQuestions_hasResults_returnsVOList() {
        QuestionEntity entity = new QuestionEntity();
        entity.setId(2L);
        entity.setTitle("Spring Boot");
        entity.setType("框架");
        entity.setDifficulty("中等");
        entity.setInterviewCount(3);
        entity.setFavorites(5);
        entity.setViews(50);
        entity.setDeleted(false);

        when(questionMapper.searchByVector(any(), anyLong(), anyInt())).thenReturn(List.of(entity));
        when(questionTagMapper.findTagIdsByQuestionIds(anyList()))
                .thenReturn(List.of(new QuestionTagEntity() {{ setQuestionId(2L); setTagId(20L); }}));

        List<QuestionDetailVO> result = questionService.searchQuestions("Spring", 1, 10, 1);

        assertEquals(1, result.size());
        assertEquals("Spring Boot", result.getFirst().title());
    }

    // 测：全文检索无结果 → 返回空列表，不调用标签查询
    @Test
    void searchQuestions_emptyResult_returnsEmptyList() {
        when(questionMapper.searchByVector(any(), anyLong(), anyInt())).thenReturn(Collections.emptyList());

        List<QuestionDetailVO> result = questionService.searchQuestions("nonexistent", 1, 10, 0);

        assertTrue(result.isEmpty());
        verify(questionTagMapper, never()).findTagIdsByQuestionIds(anyList());
    }

    // ========== getQuestion ==========

    // 测：题目存在 → 返回详情，views +1
    @Test
    void getQuestion_exists_returnsDetailAndIncrementsViews() {
        QuestionEntity entity = new QuestionEntity();
        entity.setId(1L);
        entity.setTitle("Test");
        entity.setType("Java");
        entity.setDifficulty("简单");
        entity.setInterviewCount(1);
        entity.setFavorites(0);
        entity.setViews(10);
        entity.setDeleted(false);

        when(questionMapper.selectById(1L)).thenReturn(entity);
        when(questionTagMapper.findTagIdsByQuestionId(1L)).thenReturn(Collections.emptyList());

        QuestionDetailVO vo = questionService.getQuestion(1L);

        assertEquals("Test", vo.title());
        assertEquals(11, vo.views());
        verify(questionMapper).updateById(any(QuestionEntity.class));
    }

    // 测：题目不存在 → 抛 NotFoundException
    @Test
    void getQuestion_notFound_throwsNotFoundException() {
        when(questionMapper.selectById(999L)).thenReturn(null);

        NotFoundException ex = assertThrows(NotFoundException.class, () -> questionService.getQuestion(999L));
        assertEquals(ErrorCode.QUESTION_NOT_FOUND.getCode(), ex.getCode());
    }

    // 测：题目已删除 → 抛 NotFoundException
    @Test
    void getQuestion_deleted_throwsNotFoundException() {
        QuestionEntity entity = new QuestionEntity();
        entity.setId(1L);
        entity.setDeleted(true);

        when(questionMapper.selectById(1L)).thenReturn(entity);

        NotFoundException ex = assertThrows(NotFoundException.class, () -> questionService.getQuestion(1L));
        assertEquals(ErrorCode.QUESTION_NOT_FOUND.getCode(), ex.getCode());
    }

    // 测：views 为 null → 初始化为 1
    @Test
    void getQuestion_nullViews_setsTo1() {
        QuestionEntity entity = new QuestionEntity();
        entity.setId(1L);
        entity.setTitle("Test");
        entity.setType("Java");
        entity.setDifficulty("简单");
        entity.setInterviewCount(1);
        entity.setFavorites(0);
        entity.setViews(null);
        entity.setDeleted(false);

        when(questionMapper.selectById(1L)).thenReturn(entity);
        when(questionTagMapper.findTagIdsByQuestionId(1L)).thenReturn(Collections.emptyList());

        QuestionDetailVO vo = questionService.getQuestion(1L);

        assertEquals(1, vo.views());
    }

    // ========== createQuestion ==========

    // 测：创建题目 → 插入成功并返回 VO
    @Test
    void createQuestion_validRequest_returnsVO() {
        CreateQuestionRequest request = new CreateQuestionRequest(
                "Java 基础", "什么是 JVM？", "{\"summary\":\"JVM 是...\",\"analysis\":\"...\",\"code\":\"\"}",
                "Java", "简单", "内存模型", List.of(1L, 2L)
        );

        QuestionEntity saved = new QuestionEntity();
        saved.setId(100L);
        saved.setTitle("Java 基础");
        saved.setType("Java");
        saved.setDifficulty("简单");
        saved.setInterviewCount(0);
        saved.setFavorites(0);
        saved.setViews(0);
        saved.setDeleted(false);

        doAnswer(inv -> {
            QuestionEntity e = inv.getArgument(0);
            e.setId(100L);
            return 1;
        }).when(questionMapper).insert(any(QuestionEntity.class));

        QuestionDetailVO vo = questionService.createQuestion(request);

        assertEquals("Java 基础", vo.title());
        assertEquals(100L, vo.id());

        ArgumentCaptor<QuestionTagEntity> captor = ArgumentCaptor.forClass(QuestionTagEntity.class);
        verify(questionTagMapper, times(2)).insert(captor.capture());
        assertEquals(List.of(1L, 2L), captor.getAllValues().stream().map(QuestionTagEntity::getTagId).toList());
    }

    // 测：创建题目无标签 → 不插入关联
    @Test
    void createQuestion_noTags_skipsTagInsert() {
        CreateQuestionRequest request = new CreateQuestionRequest(
                "Test", "content", "answer", "Java", "简单", null, null
        );

        doAnswer(inv -> {
            QuestionEntity e = inv.getArgument(0);
            e.setId(100L);
            return 1;
        }).when(questionMapper).insert(any(QuestionEntity.class));

        questionService.createQuestion(request);

        verify(questionTagMapper, never()).insert(any(QuestionTagEntity.class));
    }

    // ========== updateQuestion ==========

    // 测：更新题目 → 更新成功并刷新标签
    @Test
    void updateQuestion_valid_returnsUpdatedVO() {
        QuestionEntity exist = new QuestionEntity();
        exist.setId(1L);
        exist.setTitle("Old");
        exist.setType("Java");
        exist.setDifficulty("简单");
        exist.setInterviewCount(1);
        exist.setFavorites(0);
        exist.setViews(10);
        exist.setDeleted(false);

        when(questionMapper.selectById(1L)).thenReturn(exist);
        doAnswer(inv -> {
            QuestionEntity e = inv.getArgument(0);
            e.setTitle("New");
            return 1;
        }).when(questionMapper).updateById(any(QuestionEntity.class));
        when(questionTagMapper.findTagIdsByQuestionId(1L)).thenReturn(Collections.emptyList());

        UpdateQuestionRequest request = new UpdateQuestionRequest(
                "New", "new content", "new answer", "Java", "中等", null, List.of(3L)
        );

        QuestionDetailVO vo = questionService.updateQuestion(1L, request);

        assertEquals("New", vo.title());
        verify(questionTagMapper).delete(any());
        verify(questionTagMapper).insert(any(QuestionTagEntity.class));
    }

    // 测：更新不存在的题目 → 抛 NotFoundException
    @Test
    void updateQuestion_notFound_throwsNotFoundException() {
        when(questionMapper.selectById(999L)).thenReturn(null);

        UpdateQuestionRequest request = new UpdateQuestionRequest(
                "New", "content", "answer", "Java", "简单", null, null
        );

        assertThrows(NotFoundException.class, () -> questionService.updateQuestion(999L, request));
    }

    // ========== deleteQuestion ==========

    // 测：删除题目 → 软删除
    @Test
    void deleteQuestion_exists_softDeletes() {
        QuestionEntity entity = new QuestionEntity();
        entity.setId(1L);
        entity.setDeleted(false);

        when(questionMapper.selectById(1L)).thenReturn(entity);

        questionService.deleteQuestion(1L);

        assertTrue(entity.getDeleted());
        verify(questionMapper).updateById(entity);
        verify(questionMapper, never()).deleteById(anyLong());
    }

    // 测：删除不存在的题目 → 抛 NotFoundException
    @Test
    void deleteQuestion_notFound_throwsNotFoundException() {
        when(questionMapper.selectById(999L)).thenReturn(null);

        assertThrows(NotFoundException.class, () -> questionService.deleteQuestion(999L));
    }

    // 测：删除已删除的题目 → 抛 NotFoundException
    @Test
    void deleteQuestion_alreadyDeleted_throwsNotFoundException() {
        QuestionEntity entity = new QuestionEntity();
        entity.setId(1L);
        entity.setDeleted(true);

        when(questionMapper.selectById(1L)).thenReturn(entity);

        assertThrows(NotFoundException.class, () -> questionService.deleteQuestion(1L));
    }

    // ========== countQuestions ==========

    // 测：统计题目数量
    @Test
    void countQuestions_returnsCount() {
        when(questionMapper.countQuestionList(any(), any(), any(), any())).thenReturn(42L);

        long count = questionService.countQuestions(
                new QuestionListRequest(1, 20, null, null, null, null, List.of(), null));

        assertEquals(42, count);
    }

    // ========== countSearch ==========

    // 测：统计搜索数量
    @Test
    void countSearch_returnsCount() {
        when(questionMapper.countSearchByVector("java")).thenReturn(15L);

        long count = questionService.countSearch("java");

        assertEquals(15, count);
    }

    // ========== toDetailVO ==========

    // 测：AnswerVO 合法 JSON → 正确解析
    @Test
    void toDetailVO_validAnswerJson_parsesCorrectly() {
        QuestionEntity entity = new QuestionEntity();
        entity.setId(1L);
        entity.setTitle("Test");
        entity.setContent("content");
        entity.setAnswer(new AnswerVO("s", "a", "c"));
        entity.setType("Java");
        entity.setDifficulty("简单");
        entity.setInterviewCount(1);
        entity.setFavorites(0);
        entity.setViews(10);
        entity.setCreateTime(java.time.LocalDateTime.now());
        entity.setDeleted(false);

        QuestionDetailVO vo = questionService.toDetailVO(entity, List.of(), false);

        assertNotNull(vo.answer());
        assertEquals("s", vo.answer().summary());
    }

    // 测：Answer 为 null → answer VO 为 null
    @Test
    void toDetailVO_nullAnswer_returnsNullAnswerVO() {
        QuestionEntity entity = new QuestionEntity();
        entity.setId(1L);
        entity.setTitle("Test");
        entity.setContent("content");
        entity.setAnswer(null);
        entity.setType("Java");
        entity.setDifficulty("简单");
        entity.setInterviewCount(1);
        entity.setFavorites(0);
        entity.setViews(10);
        entity.setCreateTime(java.time.LocalDateTime.now());
        entity.setDeleted(false);

        QuestionDetailVO vo = questionService.toDetailVO(entity, List.of(), false);

        assertNull(vo.answer());
    }
}
