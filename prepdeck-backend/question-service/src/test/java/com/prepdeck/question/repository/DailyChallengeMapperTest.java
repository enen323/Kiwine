package com.prepdeck.question.repository;

import com.prepdeck.question.domain.DailyChallengeEntity;
import com.prepdeck.question.domain.QuestionEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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
class DailyChallengeMapperTest {

    @Autowired
    private DailyChallengeMapper dailyChallengeMapper;

    @Autowired
    private QuestionMapper questionMapper;

    @BeforeEach
    void init() {
        QuestionEntity q1 = new QuestionEntity();
        q1.setId(1L); q1.setTitle("Java 基础"); q1.setContent("什么是 JVM？"); q1.setType("Java"); q1.setDifficulty("简单"); q1.setDeleted(false); q1.setVersion(0);
        questionMapper.insert(q1);

        DailyChallengeEntity dc = new DailyChallengeEntity();
        dc.setId(1L); dc.setUserId(1L); dc.setQuestionId(1L); dc.setChallengeDate(LocalDate.of(2024, 1, 1)); dc.setCompleted(true); dc.setDeleted(false);
        dailyChallengeMapper.insert(dc);
    }

    // ========== findToday ==========

    // 测：查询今日题目
    @Test
    void findToday_existing_returnsEntity() {
        DailyChallengeEntity dc = dailyChallengeMapper.findToday(1L, LocalDate.of(2024, 1, 1));
        assertNotNull(dc);
        assertEquals(1L, dc.getUserId());
        assertEquals(1L, dc.getQuestionId());
    }

    // 测：今日无记录 → 返回 null
    @Test
    void findToday_noRecord_returnsNull() {
        DailyChallengeEntity dc = dailyChallengeMapper.findToday(999L, LocalDate.of(2024, 1, 1));
        assertNull(dc);
    }

    // ========== upsert ==========

    // 测：插入今日题目
    @Test
    void upsert_insertsNewRecord() {
        DailyChallengeEntity dc = new DailyChallengeEntity();
        dc.setUserId(2L);
        dc.setQuestionId(2L);
        dc.setChallengeDate(LocalDate.of(2024, 6, 15));
        dc.setCompleted(false);
        dc.setDeleted(false);

        int rows = dailyChallengeMapper.upsert(dc);
        assertTrue(rows > 0);
        assertNotNull(dc.getId());
    }

    // 测：重复 upsert（同用户同日期）→ 不插入新记录
    @Test
    void upsert_duplicate_doesNotInsert() {
        DailyChallengeEntity dc = new DailyChallengeEntity();
        dc.setUserId(1L);
        dc.setQuestionId(1L);
        dc.setChallengeDate(LocalDate.of(2024, 1, 1));
        dc.setCompleted(false);
        dc.setDeleted(false);

        int rows = dailyChallengeMapper.upsert(dc);
        assertEquals(0, rows);
    }
}
