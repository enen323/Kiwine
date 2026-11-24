package com.prepdeck.question.repository;

import com.prepdeck.question.domain.QuestionEntity;
import com.prepdeck.question.domain.UserFavoriteEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

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
class UserFavoriteMapperTest {

    @Autowired
    private UserFavoriteMapper userFavoriteMapper;

    @Autowired
    private QuestionMapper questionMapper;

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

        UserFavoriteEntity f1 = new UserFavoriteEntity();
        f1.setId(1L); f1.setUserId(1L); f1.setQuestionId(1L); f1.setDeleted(false); f1.setVersion(0);
        userFavoriteMapper.insert(f1);

        UserFavoriteEntity f2 = new UserFavoriteEntity();
        f2.setId(2L); f2.setUserId(1L); f2.setQuestionId(2L); f2.setDeleted(false); f2.setVersion(0);
        userFavoriteMapper.insert(f2);

        UserFavoriteEntity f3 = new UserFavoriteEntity();
        f3.setId(3L); f3.setUserId(2L); f3.setQuestionId(1L); f3.setDeleted(false); f3.setVersion(0);
        userFavoriteMapper.insert(f3);

        UserFavoriteEntity f4 = new UserFavoriteEntity();
        f4.setId(4L); f4.setUserId(1L); f4.setQuestionId(3L); f4.setDeleted(false); f4.setVersion(0);
        userFavoriteMapper.insert(f4);
        userFavoriteMapper.deleteById(4L);
    }

    // ========== findByUserAndQuestion ==========

    // 测：查询用户对题目的收藏
    @Test
    void findByUserAndQuestion_existing_returnsEntity() {
        UserFavoriteEntity fav = userFavoriteMapper.findByUserAndQuestion(1L, 1L);
        assertNotNull(fav);
        assertEquals(1L, fav.getUserId());
        assertEquals(1L, fav.getQuestionId());
        assertFalse(fav.getDeleted());
    }

    // 测：未收藏 → 返回 null
    @Test
    void findByUserAndQuestion_notFound_returnsNull() {
        UserFavoriteEntity fav = userFavoriteMapper.findByUserAndQuestion(999L, 999L);
        assertNull(fav);
    }

    // 测：收藏已删除 → 返回 null
    @Test
    void findByUserAndQuestion_deleted_returnsNull() {
        UserFavoriteEntity fav = userFavoriteMapper.findByUserAndQuestion(1L, 3L);
        assertNull(fav);
    }

    // ========== countByQuestion ==========

    // 测：统计题目的收藏数
    @Test
    void countByQuestion_returnsCount() {
        int count = userFavoriteMapper.countByQuestion(1L);
        assertTrue(count > 0);
    }

    // 测：无收藏的题目 → 0
    @Test
    void countByQuestion_noFavorites_returnsZero() {
        int count = userFavoriteMapper.countByQuestion(999L);
        assertEquals(0, count);
    }
}
