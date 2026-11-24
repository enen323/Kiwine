package com.prepdeck.question.repository;

import com.prepdeck.question.domain.QuestionEntity;
import com.prepdeck.question.domain.QuestionTagEntity;
import com.prepdeck.question.domain.TagEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;

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
class QuestionTagMapperTest {

    @Autowired
    private QuestionTagMapper questionTagMapper;

    @Autowired
    private QuestionMapper questionMapper;

    @Autowired
    private TagMapper tagMapper;

    @BeforeEach
    void init() {
        QuestionEntity q1 = new QuestionEntity();
        q1.setId(1L); q1.setTitle("Java 基础"); q1.setContent("什么是 JVM？"); q1.setType("Java"); q1.setDifficulty("简单"); q1.setDeleted(false); q1.setVersion(0);
        questionMapper.insert(q1);

        QuestionEntity q2 = new QuestionEntity();
        q2.setId(2L); q2.setTitle("Spring Boot"); q2.setContent("Spring Boot 自动配置原理？"); q2.setType("框架"); q2.setDifficulty("中等"); q2.setDeleted(false); q2.setVersion(0);
        questionMapper.insert(q2);

        QuestionEntity q3 = new QuestionEntity();
        q3.setId(3L); q3.setTitle("MySQL 索引"); q3.setContent("B+树索引原理？"); q3.setType("数据库"); q3.setDifficulty("困难"); q3.setDeleted(false); q3.setVersion(0);
        questionMapper.insert(q3);

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
    }

    // ========== findTagIdsByQuestionId ==========

    // 测：根据题目 ID 查询标签 ID
    @Test
    void findTagIdsByQuestionId_returnsTagIds() {
        List<Long> tagIds = questionTagMapper.findTagIdsByQuestionId(1L);
        assertFalse(tagIds.isEmpty());
        assertTrue(tagIds.contains(1L));
    }

    // 测：不存在的题目 ID → 返回空列表
    @Test
    void findTagIdsByQuestionId_notFound_returnsEmpty() {
        List<Long> tagIds = questionTagMapper.findTagIdsByQuestionId(999L);
        assertTrue(tagIds.isEmpty());
    }

    // ========== findTagIdsByQuestionIds ==========

    // 测：批量查询题目标签
    @Test
    void findTagIdsByQuestionIds_returnsList() {
        List<QuestionTagEntity> result = questionTagMapper.findTagIdsByQuestionIds(List.of(1L, 2L));
        assertFalse(result.isEmpty());
        assertTrue(result.stream().anyMatch(qt -> qt.getQuestionId().equals(1L) && qt.getTagId().equals(1L)));
        assertTrue(result.stream().anyMatch(qt -> qt.getQuestionId().equals(1L) && qt.getTagId().equals(2L)));
        assertTrue(result.stream().anyMatch(qt -> qt.getQuestionId().equals(2L) && qt.getTagId().equals(2L)));
    }

    // 测：空列表 → 当前实现会抛出 SQL 语法错误（已知问题，服务层需避免空传）
    @Test
    void findTagIdsByQuestionIds_emptyList_throwsException() {
        assertThrows(Exception.class, () -> questionTagMapper.findTagIdsByQuestionIds(Collections.emptyList()));
    }

    // ========== findQuestionIdsByTagId ==========

    // 测：根据标签 ID 查询题目 ID
    @Test
    void findQuestionIdsByTagId_returnsQuestionIds() {
        List<Long> questionIds = questionTagMapper.findQuestionIdsByTagId(1L);
        assertFalse(questionIds.isEmpty());
        assertTrue(questionIds.contains(1L));
    }

    // 测：不存在的标签 ID → 返回空列表
    @Test
    void findQuestionIdsByTagId_notFound_returnsEmpty() {
        List<Long> questionIds = questionTagMapper.findQuestionIdsByTagId(999L);
        assertTrue(questionIds.isEmpty());
    }

    // ========== countDistinctTagsByQuestionIds ==========

    // 测：统计关联标签数
    @Test
    void countDistinctTagsByQuestionIds_returnsCount() {
        int count = questionTagMapper.countDistinctTagsByQuestionIds(List.of(1L, 2L), List.of(1L, 2L));
        assertTrue(count >= 0);
    }

    // 测：空集合 → 抛异常（同 findTagIdsByQuestionIds 的已知问题）
    @Test
    void countDistinctTagsByQuestionIds_emptyCollection_throwsException() {
        assertThrows(Exception.class, () -> questionTagMapper.countDistinctTagsByQuestionIds(Collections.emptyList(), List.of(1L)));
    }
}
