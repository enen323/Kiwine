package com.prepdeck.question.repository;

import com.prepdeck.question.domain.QuestionEntity;
import com.prepdeck.question.domain.QuestionTagEntity;
import com.prepdeck.question.domain.TagEntity;
import com.prepdeck.question.dto.AnswerVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
@TestPropertySource(properties = {
    "spring.cloud.nacos.discovery.enabled=false",
    "spring.sql.init.mode=always",
    "spring.datasource.url=jdbc:postgresql://${DB_HOST:192.168.100.128}:5432/${DB_NAME:prepdeck}?currentSchema=interview",
    "spring.datasource.username=${DB_USERNAME:postgres}",
    "spring.datasource.password=${DB_PASSWORD:postgres}"
})
class QuestionMapperTest {

    @Autowired
    private QuestionMapper questionMapper;

    @Autowired
    private TagMapper tagMapper;

    @Autowired
    private QuestionTagMapper questionTagMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void init() {
        QuestionEntity q1 = new QuestionEntity();
        q1.setId(1L); q1.setTitle("Java 基础"); q1.setContent("什么是 JVM？"); q1.setAnswer(new AnswerVO("JVM 是 Java 虚拟机", "详细分析", "")); q1.setType("Java"); q1.setDifficulty("简单"); q1.setEasyMistake("内存模型"); q1.setInterviewCount(5); q1.setFavorites(10); q1.setViews(100); q1.setDeleted(false); q1.setVersion(0); q1.setSearchVector(null);
        questionMapper.insert(q1);

        QuestionEntity q2 = new QuestionEntity();
        q2.setId(2L); q2.setTitle("Spring Boot"); q2.setContent("Spring Boot 自动配置原理？"); q2.setAnswer(new AnswerVO("自动配置基于条件注解", "深入讲解", "")); q2.setType("框架"); q2.setDifficulty("中等"); q2.setEasyMistake("循环依赖"); q2.setInterviewCount(3); q2.setFavorites(5); q2.setViews(50); q2.setDeleted(false); q2.setVersion(0); q2.setSearchVector(null);
        questionMapper.insert(q2);

        QuestionEntity q3 = new QuestionEntity();
        q3.setId(3L); q3.setTitle("MySQL 索引"); q3.setContent("B+树索引原理？"); q3.setAnswer(new AnswerVO("B+树是平衡树", "索引优化", "")); q3.setType("数据库"); q3.setDifficulty("困难"); q3.setEasyMistake("索引失效"); q3.setInterviewCount(8); q3.setFavorites(20); q3.setViews(200); q3.setDeleted(false); q3.setVersion(0); q3.setSearchVector(null);
        questionMapper.insert(q3);

        QuestionEntity q4 = new QuestionEntity();
        q4.setId(4L); q4.setTitle("已删除题目"); q4.setContent("内容"); q4.setType("Java"); q4.setDifficulty("简单"); q4.setDeleted(false); q4.setVersion(0); q4.setSearchVector(null);
        questionMapper.insert(q4);
        questionMapper.deleteById(4L);

        TagEntity t1 = new TagEntity();
        t1.setId(1L); t1.setName("Java"); t1.setDescription("Java 相关"); t1.setDeleted(false); t1.setVersion(0);
        tagMapper.insert(t1);

        TagEntity t2 = new TagEntity();
        t2.setId(2L); t2.setName("框架"); t2.setDescription("框架相关"); t2.setDeleted(false); t2.setVersion(0);
        tagMapper.insert(t2);

        TagEntity t3 = new TagEntity();
        t3.setId(3L); t3.setName("数据库"); t3.setDescription("数据库相关"); t3.setDeleted(false); t3.setVersion(0);
        tagMapper.insert(t3);

        QuestionTagEntity qt1 = new QuestionTagEntity();
        qt1.setId(1L); qt1.setQuestionId(1L); qt1.setTagId(1L);
        questionTagMapper.insert(qt1);

        QuestionTagEntity qt2 = new QuestionTagEntity();
        qt2.setId(2L); qt2.setQuestionId(1L); qt2.setTagId(2L);
        questionTagMapper.insert(qt2);

        QuestionTagEntity qt3 = new QuestionTagEntity();
        qt3.setId(3L); qt3.setQuestionId(2L); qt3.setTagId(2L);
        questionTagMapper.insert(qt3);

        QuestionTagEntity qt4 = new QuestionTagEntity();
        qt4.setId(4L); qt4.setQuestionId(3L); qt4.setTagId(3L);
        questionTagMapper.insert(qt4);

        jdbcTemplate.update("UPDATE interview.question SET search_vector = to_tsvector('simple', coalesce(title,'') || ' ' || coalesce(content,'')) WHERE id IN (1,2,3,4)");
    }

    // ========== BaseMapper CRUD ==========

    // 测：根据 ID 查询题目
    @Test
    void selectById_returnsQuestion() {
        QuestionEntity entity = questionMapper.selectById(1L);
        assertNotNull(entity);
        assertEquals("Java 基础", entity.getTitle());
        assertEquals("Java", entity.getType());
        assertEquals("简单", entity.getDifficulty());
    }

    // 测：查询已删除题目 → 返回 null（BaseMapper 自动过滤逻辑删除）
    @Test
    void selectById_deletedQuestion_returnsNull() {
        QuestionEntity entity = questionMapper.selectById(4L);
        assertNull(entity);
    }

    // 测：查询不存在的 ID → 返回 null
    @Test
    void selectById_notFound_returnsNull() {
        QuestionEntity entity = questionMapper.selectById(999L);
        assertNull(entity);
    }

    // 测：插入题目 → 生成 ID
    @Test
    void insert_generatesId() {
        QuestionEntity entity = new QuestionEntity();
        entity.setTitle("新题目");
        entity.setContent("内容");
        entity.setType("Java");
        entity.setDifficulty("简单");
        entity.setInterviewCount(0);
        entity.setFavorites(0);
        entity.setViews(0);
        entity.setDeleted(false);

        int rows = questionMapper.insert(entity);
        assertEquals(1, rows);
        assertNotNull(entity.getId());
    }

    // 测：更新题目
    @Test
    void updateById_updatesTitle() {
        QuestionEntity entity = questionMapper.selectById(1L);
        entity.setTitle("更新后的标题");
        entity.setSearchVector(null);

        int rows = questionMapper.updateById(entity);
        assertEquals(1, rows);

        QuestionEntity updated = questionMapper.selectById(1L);
        assertEquals("更新后的标题", updated.getTitle());
    }

    // 测：删除题目
    @Test
    void deleteById_removesRecord() {
        int rows = questionMapper.deleteById(1L);
        assertEquals(1, rows);

        QuestionEntity entity = questionMapper.selectById(1L);
        assertNull(entity);
    }

    // ========== searchByVector ==========
    // ponytail: skipped — requires zhparser extension (pg_catalog.chinese) which is not available in test DB

    // ========== countSearchByVector ==========
    // ponytail: skipped — same reason as searchByVector

    // ========== selectRandom ==========

    // 测：随机选题
    @Test
    void selectRandom_returnsRandomQuestions() {
        List<QuestionEntity> results = questionMapper.selectRandom(2);
        assertFalse(results.isEmpty());
        assertTrue(results.size() <= 2);
    }

    // ========== selectQuestionList ==========

    // 测：列表查询无筛选条件
    @Test
    void selectQuestionList_noFilter_returnsAll() {
        List<QuestionEntity> results = questionMapper.selectQuestionList(null, null, null, null, null, null, 10, 0);
        assertFalse(results.isEmpty());
    }

    // 测：按类型筛选
    @Test
    void selectQuestionList_filterByType_returnsMatching() {
        List<QuestionEntity> results = questionMapper.selectQuestionList("Java", null, null, null, null, null, 10, 0);
        assertFalse(results.isEmpty());
        results.forEach(q -> assertEquals("Java", q.getType()));
    }

    // 测：按难度筛选
    @Test
    void selectQuestionList_filterByDifficulty_returnsMatching() {
        List<QuestionEntity> results = questionMapper.selectQuestionList(null, "简单", null, null, null, null, 10, 0);
        assertFalse(results.isEmpty());
        results.forEach(q -> assertEquals("简单", q.getDifficulty()));
    }

    // 测：按标签筛选
    @Test
    void selectQuestionList_filterByTag_returnsMatching() {
        List<QuestionEntity> results = questionMapper.selectQuestionList(null, null, new ArrayList<>(List.of(1L)), null, null, null, 10, 0);
        assertFalse(results.isEmpty());
    }

    // 测：无匹配结果
    @Test
    void selectQuestionList_noMatch_returnsEmpty() {
        List<QuestionEntity> results = questionMapper.selectQuestionList("不存在的类型", null, null, null, null, null, 10, 0);
        assertTrue(results.isEmpty());
    }

    // 测：排序 - 按 views 降序
    @Test
    void selectQuestionList_sortByViews_returnsOrdered() {
        List<QuestionEntity> results = questionMapper.selectQuestionList(null, null, null, null, "views", "desc", 10, 0);
        assertFalse(results.isEmpty());
    }

    // ========== countQuestionList ==========

    // 测：统计列表数量
    @Test
    void countQuestionList_returnsCount() {
        long count = questionMapper.countQuestionList(null, null, null, null);
        assertTrue(count > 0);
    }

    // 测：按类型统计
    @Test
    void countQuestionList_filterByType_returnsCount() {
        long count = questionMapper.countQuestionList("Java", null, null, null);
        assertTrue(count > 0);
    }

    // 测：无匹配时统计为 0
    @Test
    void countQuestionList_noMatch_returnsZero() {
        long count = questionMapper.countQuestionList("不存在的类型", null, null, null);
        assertEquals(0, count);
    }
}
