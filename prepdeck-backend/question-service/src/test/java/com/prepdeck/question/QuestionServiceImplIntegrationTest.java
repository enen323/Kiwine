package com.prepdeck.question;

import com.prepdeck.question.domain.QuestionEntity;
import com.prepdeck.question.domain.QuestionTagEntity;
import com.prepdeck.question.domain.TagEntity;
import com.prepdeck.question.dto.AnswerVO;
import com.prepdeck.question.repository.QuestionMapper;
import com.prepdeck.question.repository.QuestionTagMapper;
import com.prepdeck.question.repository.TagMapper;
import com.prepdeck.question.repository.UserFavoriteMapper;
import com.prepdeck.question.service.QuestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.sql.init.mode=always",
        "spring.datasource.url=jdbc:postgresql://${DB_HOST:192.168.100.128}:5432/${DB_NAME:prepdeck}?currentSchema=interview",
        "spring.datasource.username=${DB_USERNAME:postgres}",
        "spring.datasource.password=${DB_PASSWORD:postgres}"
})
class QuestionServiceImplIntegrationTest {

    @Autowired
    private QuestionService questionService;

    @Autowired
    private QuestionMapper questionMapper;

    @Autowired
    private QuestionTagMapper questionTagMapper;

    @Autowired
    private TagMapper tagMapper;

    @Autowired
    private UserFavoriteMapper userFavoriteMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void init() {
        // 清理并插入基础题目数据
        jdbcTemplate.update("DELETE FROM interview.question_tag");
        jdbcTemplate.update("DELETE FROM interview.user_favorite");
        jdbcTemplate.update("DELETE FROM interview.question WHERE id > 0");
        jdbcTemplate.update("DELETE FROM interview.tag WHERE id > 0");

        TagEntity t1 = new TagEntity();
        t1.setId(1L); t1.setName("Java"); t1.setDescription("Java 相关"); t1.setDeleted(false); t1.setVersion(0);
        tagMapper.insert(t1);

        TagEntity t2 = new TagEntity();
        t2.setId(2L); t2.setName("框架"); t2.setDescription("框架相关"); t2.setDeleted(false); t2.setVersion(0);
        tagMapper.insert(t2);

        TagEntity t3 = new TagEntity();
        t3.setId(3L); t3.setName("数据库"); t3.setDescription("数据库相关"); t3.setDeleted(false); t3.setVersion(0);
        tagMapper.insert(t3);

        QuestionEntity q1 = new QuestionEntity();
        q1.setId(1L); q1.setTitle("Java 基础"); q1.setContent("什么是 JVM？");
        q1.setAnswer(new AnswerVO("JVM", "分析", ""));
        q1.setType("Java"); q1.setDifficulty("简单"); q1.setEasyMistake("内存模型");
        q1.setInterviewCount(5); q1.setFavorites(10); q1.setViews(100);
        q1.setDeleted(false); q1.setVersion(0); q1.setSearchVector(null);
        questionMapper.insert(q1);

        QuestionEntity q2 = new QuestionEntity();
        q2.setId(2L); q2.setTitle("Spring Boot"); q2.setContent("自动配置原理？");
        q2.setAnswer(new AnswerVO("自动配置", "分析", ""));
        q2.setType("框架"); q2.setDifficulty("中等"); q2.setEasyMistake("循环依赖");
        q2.setInterviewCount(3); q2.setFavorites(5); q2.setViews(50);
        q2.setDeleted(false); q2.setVersion(0); q2.setSearchVector(null);
        questionMapper.insert(q2);

        QuestionEntity q3 = new QuestionEntity();
        q3.setId(3L); q3.setTitle("MySQL 索引"); q3.setContent("B+树原理？");
        q3.setAnswer(new AnswerVO("B+树", "分析", ""));
        q3.setType("数据库"); q3.setDifficulty("困难"); q3.setEasyMistake("索引失效");
        q3.setInterviewCount(8); q3.setFavorites(20); q3.setViews(200);
        q3.setDeleted(false); q3.setVersion(0); q3.setSearchVector(null);
        questionMapper.insert(q3);

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

        jdbcTemplate.update(
                "UPDATE interview.question SET search_vector = to_tsvector('simple', coalesce(title,'') || ' ' || coalesce(content,'')) WHERE id IN (1,2,3)");
    }

    // ========== getQuestion ==========

    // 测：真实 DB 下 getQuestion 不触发只读事务错误，views 正确 +1
    @Test
    void getQuestion_realDB_incrementsViewsWithoutReadOnlyError() {
        var vo = questionService.getQuestion(1L);

        assertEquals("Java 基础", vo.title());
        assertEquals(101, vo.views()); // 100 + 1
    }

    // 测：真实 DB 下 views 为 null → 初始化为 1
    @Test
    void getQuestion_realDB_nullViews_setsTo1() {
        QuestionEntity q = new QuestionEntity();
        q.setId(10L); q.setTitle("新题"); q.setContent("内容");
        q.setType("Java"); q.setDifficulty("简单");
        q.setInterviewCount(0); q.setFavorites(0); q.setViews(null);
        q.setDeleted(false); q.setVersion(0); q.setSearchVector(null);
        questionMapper.insert(q);

        var vo = questionService.getQuestion(10L);

        assertEquals(1, vo.views());
    }

    // 测：真实 DB 下查询不存在的题目 → 抛 NotFoundException
    @Test
    void getQuestion_realDB_notFound_throws() {
        assertThrows(com.prepdeck.common.exception.NotFoundException.class,
                () -> questionService.getQuestion(999L));
    }

    // 测：真实 DB 下 updateById 不会向 tsvector 列写入 varchar
    @Test
    void getQuestion_realDB_doesNotWriteSearchVector() {
        questionService.getQuestion(1L);

        String sv = jdbcTemplate.queryForObject(
                "SELECT search_vector FROM interview.question WHERE id = 1", String.class);
        assertNotNull(sv);
    }

    // ========== createQuestion ==========

    // 测：真实 DB 下创建题目 → 插入成功并返回 VO
    @Test
    void createQuestion_realDB_returnsVO() {
        var request = new com.prepdeck.question.dto.CreateQuestionRequest(
                "新题目", "内容", "{\"summary\":\"s\",\"analysis\":\"a\",\"code\":\"\"}",
                "Java", "简单", null, List.of(1L));

        var vo = questionService.createQuestion(request);

        assertNotNull(vo.id());
        assertEquals("新题目", vo.title());
        assertEquals(1, vo.tags().size());
        assertEquals(1L, vo.tags().getFirst().id());
    }

    // 测：真实 DB 下创建无标签题目 → 标签列表为空
    @Test
    void createQuestion_realDB_noTags_skipsTagInsert() {
        var request = new com.prepdeck.question.dto.CreateQuestionRequest(
                "无标签题", "内容", "答案", "Java", "简单", null, null);

        var vo = questionService.createQuestion(request);

        assertNotNull(vo.id());
        assertTrue(vo.tags().isEmpty());
    }

    // ========== updateQuestion ==========

    // 测：真实 DB 下更新题目 → 标题变更，标签刷新
    @Test
    void updateQuestion_realDB_updatesTitleAndTags() {
        var request = new com.prepdeck.question.dto.UpdateQuestionRequest(
                "更新后标题", "新内容", "新答案", "Java", "中等", null, List.of(3L));

        var vo = questionService.updateQuestion(1L, request);

        assertEquals("更新后标题", vo.title());
        assertEquals(1, vo.tags().size());
        assertEquals(3L, vo.tags().getFirst().id());
    }

    // 测：真实 DB 下更新不存在的题目 → 抛 NotFoundException
    @Test
    void updateQuestion_realDB_notFound_throws() {
        var request = new com.prepdeck.question.dto.UpdateQuestionRequest(
                "标题", "内容", "答案", "Java", "简单", null, null);

        assertThrows(com.prepdeck.common.exception.NotFoundException.class,
                () -> questionService.updateQuestion(999L, request));
    }

    // ========== listQuestions ==========

    // 测：真实 DB 下列表查询 → 返回分页结果
    @Test
    void listQuestions_realDB_returnsPagedResults() {
        var request = new com.prepdeck.question.dto.QuestionListRequest(
                1, 20, null, null, "Java", null, null, null);

        var results = questionService.listQuestions(request, 3);

        assertFalse(results.isEmpty());
        assertEquals("Java 基础", results.getFirst().title());
    }

    // 测：真实 DB 下按标签筛选 → 只返回含该标签的题目
    @Test
    void listQuestions_realDB_filterByTag_returnsMatching() {
        var request = new com.prepdeck.question.dto.QuestionListRequest(
                1, 20, null, null, null, null, List.of(2L), null);

        var results = questionService.listQuestions(request, 3);

        assertFalse(results.isEmpty());
        results.forEach(q -> assertEquals("Spring Boot", q.title()));
    }

    // ========== searchQuestions ==========

    // 测：真实 DB 下全文检索 → 返回匹配结果
    @Test
    void searchQuestions_realDB_returnsMatchingResults() {
        var results = questionService.searchQuestions("Java", 1, 10, 3);

        assertFalse(results.isEmpty());
    }

    // ========== deleteQuestion ==========

    // 测：真实 DB 下软删除 → deleted 变为 true，查询时被过滤
    @Test
    void deleteQuestion_realDB_softDeletes() {
        questionService.deleteQuestion(1L);

        QuestionEntity deleted = questionMapper.selectById(1L);
        assertNull(deleted); // 逻辑删除后 BaseMapper 自动过滤
    }
}
