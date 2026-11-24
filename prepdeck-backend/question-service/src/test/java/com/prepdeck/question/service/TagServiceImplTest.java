package com.prepdeck.question.service;

import com.prepdeck.question.dto.CreateTagRequest;
import com.prepdeck.question.dto.TagVO;
import com.prepdeck.question.dto.UpdateTagRequest;
import com.prepdeck.question.repository.TagMapper;
import com.prepdeck.question.domain.TagEntity;
import com.prepdeck.common.enums.ErrorCode;
import com.prepdeck.common.exception.DuplicateException;
import com.prepdeck.common.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TagServiceImplTest {

    @Mock private TagMapper tagMapper;

    private TagServiceImpl tagService;

    @BeforeEach
    void setUp() {
        tagService = new TagServiceImpl(tagMapper);
    }

    // ========== listTags ==========

    // 测：标签列表 → 返回未删除标签
    @Test
    void listTags_returnsActiveTags() {
        TagEntity tag = new TagEntity();
        tag.setId(1L);
        tag.setName("Java");
        tag.setDescription("Java 相关");
        tag.setCreateTime(LocalDateTime.now());
        tag.setDeleted(false);

        when(tagMapper.selectList(null)).thenReturn(List.of(tag, new TagEntity() {{
            setId(2L); setDeleted(true);
        }}));

        List<TagVO> result = tagService.listTags();

        assertEquals(1, result.size());
        assertEquals("Java", result.getFirst().name());
    }

    // 测：无标签 → 返回空列表
    @Test
    void listTags_noTags_returnsEmptyList() {
        when(tagMapper.selectList(null)).thenReturn(Collections.emptyList());

        List<TagVO> result = tagService.listTags();

        assertTrue(result.isEmpty());
    }

    // ========== createTag ==========

    // 测：创建标签 → 成功返回
    @Test
    void createTag_valid_returnsVO() {
        when(tagMapper.selectOne(any())).thenReturn(null);

        TagEntity saved = new TagEntity();
        saved.setId(10L);
        saved.setName("Python");
        saved.setDescription("Python 相关");
        saved.setCreateTime(LocalDateTime.now());

        doAnswer(inv -> {
            TagEntity e = inv.getArgument(0);
            e.setId(10L);
            e.setCreateTime(LocalDateTime.now());
            return 1;
        }).when(tagMapper).insert(any(TagEntity.class));

        TagVO vo = tagService.createTag(new CreateTagRequest("Python", "Python 相关"));

        assertEquals(10L, vo.id());
        assertEquals("Python", vo.name());
    }

    // 测：标签名已存在 → 抛 DuplicateException
    @Test
    void createTag_duplicateName_throwsDuplicateException() {
        TagEntity exist = new TagEntity();
        exist.setId(5L);
        exist.setName("Java");
        exist.setDeleted(false);

        when(tagMapper.selectOne(any())).thenReturn(exist);

        assertThrows(DuplicateException.class, () -> tagService.createTag(new CreateTagRequest("Java", null)));
    }

    // 测：标签名已被删除 → 可重新创建同名标签
    @Test
    void createTag_deletedDuplicate_allowsCreation() {
        TagEntity exist = new TagEntity();
        exist.setId(5L);
        exist.setName("Java");
        exist.setDeleted(true);

        when(tagMapper.selectOne(any())).thenReturn(exist);

        doAnswer(inv -> {
            TagEntity e = inv.getArgument(0);
            e.setId(10L);
            return 1;
        }).when(tagMapper).insert(any(TagEntity.class));

        TagVO vo = tagService.createTag(new CreateTagRequest("Java", "new desc"));

        assertEquals(10L, vo.id());
        verify(tagMapper).insert(any(TagEntity.class));
    }

    // ========== updateTag ==========

    // 测：更新标签 → 成功
    @Test
    void updateTag_valid_returnsUpdatedVO() {
        TagEntity exist = new TagEntity();
        exist.setId(1L);
        exist.setName("Old");
        exist.setDescription("old desc");
        exist.setDeleted(false);

        when(tagMapper.selectById(1L)).thenReturn(exist);

        TagVO vo = tagService.updateTag(1L, new UpdateTagRequest("New", "new desc"));

        assertEquals("New", vo.name());
        assertEquals("new desc", vo.description());
        verify(tagMapper).updateById(exist);
    }

    // 测：更新不存在的标签 → 抛 NotFoundException
    @Test
    void updateTag_notFound_throwsNotFoundException() {
        when(tagMapper.selectById(999L)).thenReturn(null);

        assertThrows(NotFoundException.class,
                () -> tagService.updateTag(999L, new UpdateTagRequest("New", "desc")));
    }

    // 测：更新已删除的标签 → 抛 NotFoundException
    @Test
    void updateTag_deleted_throwsNotFoundException() {
        TagEntity exist = new TagEntity();
        exist.setId(1L);
        exist.setDeleted(true);

        when(tagMapper.selectById(1L)).thenReturn(exist);

        assertThrows(NotFoundException.class,
                () -> tagService.updateTag(1L, new UpdateTagRequest("New", "desc")));
    }

    // ========== deleteTag ==========

    // 测：删除标签 → 软删除
    @Test
    void deleteTag_exists_softDeletes() {
        TagEntity exist = new TagEntity();
        exist.setId(1L);
        exist.setDeleted(false);

        when(tagMapper.selectById(1L)).thenReturn(exist);

        tagService.deleteTag(1L);

        assertTrue(exist.getDeleted());
        verify(tagMapper).updateById(exist);
        verify(tagMapper, never()).deleteById(anyLong());
    }

    // 测：删除不存在的标签 → 抛 NotFoundException
    @Test
    void deleteTag_notFound_throwsNotFoundException() {
        when(tagMapper.selectById(999L)).thenReturn(null);

        assertThrows(NotFoundException.class, () -> tagService.deleteTag(999L));
    }

    // 测：删除已删除的标签 → 抛 NotFoundException
    @Test
    void deleteTag_alreadyDeleted_throwsNotFoundException() {
        TagEntity exist = new TagEntity();
        exist.setId(1L);
        exist.setDeleted(true);

        when(tagMapper.selectById(1L)).thenReturn(exist);

        assertThrows(NotFoundException.class, () -> tagService.deleteTag(1L));
    }
}
