package com.prepdeck.resume.dto.response;

import com.prepdeck.resume.domain.ResumeEntity;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ResumeVOTest {

    @Test
    void fromEntityFullyPopulated() {
        ResumeEntity entity = new ResumeEntity();
        entity.setId(1L);
        entity.setUserId(100L);
        entity.setFileName("resume.pdf");
        entity.setFileUrl("uploads/resume.pdf");
        entity.setContentText("my content");
        entity.setContentJson("{\"skills\":[\"Java\"]}");
        entity.setIsActive(true);
        entity.setCreateTime(LocalDateTime.of(2025, 6, 1, 12, 0));
        entity.setUpdateTime(LocalDateTime.of(2025, 6, 15, 12, 0));

        ResumeVO vo = ResumeVO.fromEntity(entity);

        assertEquals(1L, vo.id());
        assertEquals(100L, vo.userId());
        assertEquals("resume.pdf", vo.fileName());
        assertEquals("uploads/resume.pdf", vo.fileUrl());
        assertEquals("my content", vo.contentText());
        assertEquals("{\"skills\":[\"Java\"]}", vo.contentJson());
        assertTrue(vo.isActive());
        assertEquals(LocalDateTime.of(2025, 6, 1, 12, 0), vo.createTime());
        assertEquals(LocalDateTime.of(2025, 6, 15, 12, 0), vo.updateTime());
    }

    @Test
    void fromEntityNullFields() {
        ResumeEntity entity = new ResumeEntity();
        entity.setId(1L);
        entity.setUserId(100L);

        ResumeVO vo = ResumeVO.fromEntity(entity);

        assertEquals(1L, vo.id());
        assertEquals(100L, vo.userId());
        assertNull(vo.fileName());
        assertNull(vo.fileUrl());
        assertNull(vo.contentText());
        assertNull(vo.contentJson());
        assertNull(vo.isActive());
    }
}
