package com.prepdeck.question.repository;

import com.prepdeck.question.domain.TagEntity;
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
class TagMapperTest {

    @Autowired
    private TagMapper tagMapper;

    @BeforeEach
    void init() {
        TagEntity t1 = new TagEntity();
        t1.setId(1L); t1.setName("Java"); t1.setDescription("Java 相关"); t1.setDeleted(false); t1.setVersion(0);
        tagMapper.insert(t1);

        TagEntity t2 = new TagEntity();
        t2.setId(2L); t2.setName("框架"); t2.setDescription("框架相关"); t2.setDeleted(false); t2.setVersion(0);
        tagMapper.insert(t2);

        TagEntity t3 = new TagEntity();
        t3.setId(3L); t3.setName("数据库"); t3.setDescription("数据库相关"); t3.setDeleted(false); t3.setVersion(0);
        tagMapper.insert(t3);

        TagEntity t4 = new TagEntity();
        t4.setId(4L); t4.setName("已删除标签"); t4.setDescription("desc"); t4.setDeleted(false); t4.setVersion(0);
        tagMapper.insert(t4);
        tagMapper.deleteById(4L);
    }

    // 测：查询所有标签
    @Test
    void selectList_returnsAllTags() {
        List<TagEntity> tags = tagMapper.selectList(null);
        assertFalse(tags.isEmpty());
    }

    // 测：根据 ID 查询
    @Test
    void selectById_returnsTag() {
        TagEntity tag = tagMapper.selectById(1L);
        assertNotNull(tag);
        assertEquals("Java", tag.getName());
    }

    // 测：查询已删除标签 → 返回 null（BaseMapper 自动过滤逻辑删除）
    @Test
    void selectById_deletedTag_returnsNull() {
        TagEntity tag = tagMapper.selectById(4L);
        assertNull(tag);
    }

    // 测：插入标签
    @Test
    void insert_createsTag() {
        TagEntity tag = new TagEntity();
        tag.setName("新标签");
        tag.setDescription("描述");
        tag.setDeleted(false);

        int rows = tagMapper.insert(tag);
        assertEquals(1, rows);
        assertNotNull(tag.getId());
    }

    // 测：更新标签
    @Test
    void updateById_updatesName() {
        TagEntity tag = tagMapper.selectById(1L);
        tag.setName("更新后");

        int rows = tagMapper.updateById(tag);
        assertEquals(1, rows);

        TagEntity updated = tagMapper.selectById(1L);
        assertEquals("更新后", updated.getName());
    }

    // 测：删除标签
    @Test
    void deleteById_removesTag() {
        int rows = tagMapper.deleteById(1L);
        assertEquals(1, rows);

        TagEntity tag = tagMapper.selectById(1L);
        assertNull(tag);
    }
}
