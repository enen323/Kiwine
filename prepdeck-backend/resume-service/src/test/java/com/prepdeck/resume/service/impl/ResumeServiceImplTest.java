package com.prepdeck.resume.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.prepdeck.common.ai.AiClient;
import com.prepdeck.common.enums.ErrorCode;
import com.prepdeck.common.exception.BadRequestException;
import com.prepdeck.common.exception.ForbiddenException;
import com.prepdeck.common.exception.NotFoundException;
import com.prepdeck.resume.domain.ResumeEntity;
import com.prepdeck.resume.domain.ResumeSectionEntity;
import com.prepdeck.resume.dto.request.PolishRequest;
import com.prepdeck.resume.dto.request.ResumeCreateRequest;
import com.prepdeck.resume.dto.request.ResumeUpdateRequest;
import com.prepdeck.resume.dto.response.PolishResponse;
import com.prepdeck.resume.dto.response.ResumeVO;
import com.prepdeck.resume.repository.ResumeMapper;
import com.prepdeck.resume.repository.ResumeSectionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResumeServiceImplTest {

    @Mock
    private ResumeMapper resumeMapper;

    @Mock
    private ResumeSectionMapper resumeSectionMapper;

    @Mock
    private AiClient aiClient;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @TempDir
    Path tempDir;

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
    private ResumeServiceImpl resumeService;

    @BeforeEach
    void setUp() {
        resumeService = new ResumeServiceImpl(resumeMapper, resumeSectionMapper, aiClient, objectMapper, stringRedisTemplate, null);
        ReflectionTestUtils.setField(resumeService, "uploadDir", tempDir.toString());
    }

    // ========== create ==========

    @Test
    void createFirstResumeAutoActivates() {
        when(resumeMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        ResumeEntity[] captured = new ResumeEntity[1];
        doAnswer(inv -> { ResumeEntity e = inv.getArgument(0); e.setId(1L); captured[0] = e; return 1; })
                .when(resumeMapper).insert(any(ResumeEntity.class));
        when(resumeSectionMapper.insert(any(com.prepdeck.resume.domain.ResumeSectionEntity.class))).thenReturn(1);
        when(resumeMapper.selectById(1L)).thenAnswer(inv -> captured[0]);
        when(resumeSectionMapper.findByResumeId(1L)).thenReturn(List.of());

        ResumeCreateRequest request = new ResumeCreateRequest("resume1.txt", "my resume content", null, null, null, null);
        ResumeVO vo = resumeService.create(1L, request);

        assertNotNull(vo);
        assertEquals("resume1.txt", vo.fileName());
        assertEquals("my resume content", vo.contentText());
        assertTrue(vo.isActive());

        ArgumentCaptor<ResumeEntity> captor = ArgumentCaptor.forClass(ResumeEntity.class);
        verify(resumeMapper).insert(captor.capture());
        assertTrue(captor.getValue().getIsActive());
    }

    @Test
    void createSecondResumeNotActive() {
        when(resumeMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        ResumeEntity[] captured = new ResumeEntity[1];
        doAnswer(inv -> { ResumeEntity e = inv.getArgument(0); e.setId(2L); captured[0] = e; return 1; })
                .when(resumeMapper).insert(any(ResumeEntity.class));
        when(resumeSectionMapper.insert(any(com.prepdeck.resume.domain.ResumeSectionEntity.class))).thenReturn(1);
        when(resumeMapper.selectById(2L)).thenAnswer(inv -> captured[0]);
        when(resumeSectionMapper.findByResumeId(2L)).thenReturn(List.of());

        ResumeCreateRequest request = new ResumeCreateRequest("resume2.txt", "another resume", null, null, null, null);
        ResumeVO vo = resumeService.create(1L, request);

        assertNotNull(vo);
        assertFalse(vo.isActive());
    }

    // ========== update ==========

    @Test
    void updateSuccess() {
        ResumeEntity existing = createEntity(1L, 1L);
        when(resumeMapper.selectById(1L)).thenReturn(existing);
        when(resumeMapper.updateById(any(ResumeEntity.class))).thenReturn(1);

        ResumeUpdateRequest request = new ResumeUpdateRequest("updated.txt", "new content", null, true, null, null, null, null, null, null);
        ResumeVO vo = resumeService.update(1L, 1L, request);

        assertNotNull(vo);
        assertEquals("updated.txt", vo.fileName());
        assertEquals("new content", vo.contentText());
        assertTrue(vo.isActive());
    }

    @Test
    void updateNotFound() {
        when(resumeMapper.selectById(99L)).thenReturn(null);

        ResumeUpdateRequest request = new ResumeUpdateRequest("x", null, null, null, null, null, null, null, null, null);
        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> resumeService.update(99L, 1L, request));
        assertEquals(ErrorCode.RESUME_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void updateWrongUser() {
        ResumeEntity existing = createEntity(1L, 2L);  // belongs to userId=2
        when(resumeMapper.selectById(1L)).thenReturn(existing);

        ResumeUpdateRequest request = new ResumeUpdateRequest("x", null, null, null, null, null, null, null, null, null);
        ForbiddenException ex = assertThrows(ForbiddenException.class,
                () -> resumeService.update(1L, 1L, request));
        assertEquals(ErrorCode.FORBIDDEN.getCode(), ex.getCode());
    }

    // ========== getById ==========

    @Test
    void getByIdSuccess() {
        ResumeEntity entity = createEntity(1L, 1L);
        when(resumeMapper.selectById(1L)).thenReturn(entity);

        ResumeVO vo = resumeService.getById(1L, 1L);
        assertNotNull(vo);
        assertEquals(1L, vo.id());
    }

    @Test
    void getByIdNotFound() {
        when(resumeMapper.selectById(99L)).thenReturn(null);

        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> resumeService.getById(99L, 1L));
        assertEquals(ErrorCode.RESUME_NOT_FOUND.getCode(), ex.getCode());
    }

    // ========== delete ==========

    @Test
    void deleteSuccess() {
        ResumeEntity entity = createEntity(1L, 1L);
        when(resumeMapper.selectById(1L)).thenReturn(entity);
        when(resumeSectionMapper.deleteByResumeId(1L)).thenReturn(6);
        when(resumeMapper.deleteById(1L)).thenReturn(1);

        assertDoesNotThrow(() -> resumeService.delete(1L, 1L));
        verify(resumeMapper).deleteById(1L);
    }

    @Test
    void deleteNotFound() {
        when(resumeMapper.selectById(99L)).thenReturn(null);
        assertThrows(NotFoundException.class, () -> resumeService.delete(99L, 1L));
    }

    // ========== setActive ==========

    @Test
    void setActiveSuccess() {
        ResumeEntity entity = createEntity(1L, 1L);
        when(resumeMapper.selectById(1L)).thenReturn(entity);
        when(resumeMapper.update(any(ResumeEntity.class), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(resumeMapper.updateById(any(ResumeEntity.class))).thenReturn(1);

        ResumeVO vo = resumeService.setActive(1L, 1L);
        assertNotNull(vo);
        assertTrue(vo.isActive());
    }

    // ========== uploadFile ==========

    @Test
    void uploadFileNull() {
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> resumeService.uploadFile(1L, null));
        assertEquals(ErrorCode.RESUME_PARSE_FAILED.getCode(), ex.getCode());
    }

    @Test
    void uploadFileEmpty() {
        MultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", new byte[0]);
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> resumeService.uploadFile(1L, file));
        assertEquals(ErrorCode.RESUME_PARSE_FAILED.getCode(), ex.getCode());
    }

    @Test
    void uploadFileNonPdfContentType() {
        MultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "content".getBytes());
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> resumeService.uploadFile(1L, file));
        assertEquals(ErrorCode.RESUME_PARSE_FAILED.getCode(), ex.getCode());
    }

    @Test
    void uploadFileTooLarge() {
        byte[] largeContent = new byte[11 * 1024 * 1024]; // 11 MB
        MultipartFile file = new MockMultipartFile("file", "large.pdf", "application/pdf", largeContent);
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> resumeService.uploadFile(1L, file));
        assertEquals(ErrorCode.RESUME_TOO_LARGE.getCode(), ex.getCode());
    }

    @Test
    void uploadFileSuccessPdf() {
        byte[] content = "fake pdf content".getBytes();
        MultipartFile file = new MockMultipartFile("file", "resume.pdf", "application/pdf", content);
        when(resumeMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(resumeMapper.insert(any(ResumeEntity.class))).thenReturn(1);

        ResumeVO vo = resumeService.uploadFile(1L, file);

        assertNotNull(vo);
        assertEquals("resume.pdf", vo.fileName());
        assertNotNull(vo.fileUrl());
        assertTrue(vo.fileUrl().contains("resume.pdf"));
        assertTrue(vo.isActive());
    }

    // ========== polishSection ==========

    @Test
    void polishSectionSuccess() {
        String raw = "Java Spring Boot 后端开发";
        when(aiClient.chat(anyString(), argThat(msg -> msg.contains("<example>") && msg.contains("</example>"))))
                .thenReturn("精通Java和Spring Boot的后端开发工程师");

        PolishRequest request = new PolishRequest("experience", raw, null);
        PolishResponse response = resumeService.polishSection(1L, request);

        assertNotNull(response);
        assertEquals("精通Java和Spring Boot的后端开发工程师", response.polishedText());
    }

    @Test
    void polishSectionIncludesExampleInUserMessage() {
        String raw = "腾讯 前端开发";
        when(aiClient.chat(anyString(), argThat(msg ->
                msg.contains("<example>\n") && msg.contains("\n</example>") && msg.contains(raw))))
                .thenReturn("腾讯 | 前端开发工程师 | 2020.07-2023.03");

        PolishRequest request = new PolishRequest("experience", raw, null);
        PolishResponse response = resumeService.polishSection(1L, request);

        assertNotNull(response);
    }

    @Test
    void polishSectionUnknownTypeReturnsRaw() {
        PolishRequest request = new PolishRequest("unknown", "some text", null);
        PolishResponse response = resumeService.polishSection(1L, request);

        assertEquals("some text", response.polishedText());
        verify(aiClient, never()).chat(any(), any());
    }

    @Test
    void polishSectionEmptyRawReturnsEmpty() {
        PolishRequest request = new PolishRequest("experience", "", null);
        PolishResponse response = resumeService.polishSection(1L, request);

        assertEquals("", response.polishedText());
        verify(aiClient, never()).chat(any(), any());
    }

    @Test
    void polishSectionAiErrorThrowsBadRequest() {
        when(aiClient.chat(any(), any())).thenThrow(new RuntimeException("API error"));

        PolishRequest request = new PolishRequest("experience", "Java Spring Boot", null);
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> resumeService.polishSection(1L, request));
        assertEquals(ErrorCode.LLM_SERVICE_ERROR.getCode(), ex.getCode());
    }

    @Test
    void polishSectionJsonFormatSuccess() throws Exception {
        String rawArray = "[\"负责核心交易系统\",\"优化支付链路\"]";
        when(aiClient.chat(anyString(), anyString(), eq("json_object")))
                .thenReturn("[\"负责核心交易系统的设计与开发\",\"优化支付链路并提升成功率\"]");

        PolishRequest request = new PolishRequest("experience", rawArray, "json");
        PolishResponse response = resumeService.polishSection(1L, request);

        assertNotNull(response);
        String expected = "[\"负责核心交易系统的设计与开发\",\"优化支付链路并提升成功率\"]";
        assertEquals(objectMapper.readTree(expected), objectMapper.readTree(response.polishedText()));
    }

    @Test
    void polishSectionJsonFormatInvalidArray() {
        PolishRequest request = new PolishRequest("experience", "not a json array", "json");
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> resumeService.polishSection(1L, request));
        assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    @Test
    void polishSectionJsonFormatAiReturnsNonArray() {
        when(aiClient.chat(anyString(), anyString(), eq("json_object")))
                .thenReturn("\"just a string\"");

        PolishRequest request = new PolishRequest("experience", "[\"item\"]", "json");
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> resumeService.polishSection(1L, request));
        assertEquals(ErrorCode.LLM_SERVICE_ERROR.getCode(), ex.getCode());
    }

    // ========== polishSection batch ==========

    @Test
    void polishBatchAllSections() throws Exception {
        ResumeEntity entity = createEntity(1L, 1L);
        when(resumeMapper.selectById(1L)).thenReturn(entity);
        List<ResumeSectionEntity> sections = List.of(
                createSection(1L, 1L, "summary", "{\"text\":\"7年Java后端经验\"}"),
                createSection(2L, 1L, "education", "{\"school\":\"清华大学\",\"major\":\"计算机 本科\"}")
        );
        when(resumeSectionMapper.findByResumeId(1L)).thenReturn(sections);
        when(aiClient.chat(anyString(), anyString()))
                .thenReturn("7年Java后端开发经验的高级工程师")
                .thenReturn("清华大学 计算机科学与技术 本科");
        when(resumeSectionMapper.updateById(any(ResumeSectionEntity.class))).thenReturn(1);

        PolishRequest request = new PolishRequest(null, null, null, 1L, null);
        PolishResponse response = resumeService.polishSection(1L, request);

        assertNull(response.polishedText());
        assertNotNull(response.sections());
        assertEquals(2, response.sections().size());
        assertEquals("summary", response.sections().get(0).sectionType());
        assertEquals("education", response.sections().get(1).sectionType());
        verify(resumeSectionMapper, times(2)).updateById(any(ResumeSectionEntity.class));
    }

    @Test
    void polishBatchSpecificSections() throws Exception {
        ResumeEntity entity = createEntity(1L, 1L);
        when(resumeMapper.selectById(1L)).thenReturn(entity);
        List<ResumeSectionEntity> sections = List.of(
                createSection(1L, 1L, "summary", "{\"text\":\"7年Java后端经验\"}"),
                createSection(2L, 1L, "education", "{\"school\":\"清华大学\",\"major\":\"计算机 本科\"}"),
                createSection(3L, 1L, "experience", "{\"company\":\"腾讯\",\"position\":\"后端开发\"}")
        );
        when(resumeSectionMapper.findByResumeId(1L)).thenReturn(sections);
        when(aiClient.chat(anyString(), anyString()))
                .thenReturn("7年Java后端开发经验的高级工程师");
        when(resumeSectionMapper.updateById(any(ResumeSectionEntity.class))).thenReturn(1);

        PolishRequest request = new PolishRequest(null, null, null, 1L, List.of("summary"));
        PolishResponse response = resumeService.polishSection(1L, request);

        assertEquals(1, response.sections().size());
        assertEquals("summary", response.sections().get(0).sectionType());
        verify(resumeSectionMapper, times(1)).updateById(any(ResumeSectionEntity.class));
    }

    @Test
    void polishBatchUnknownTypesReturnsRaw() {
        ResumeEntity entity = createEntity(1L, 1L);
        when(resumeMapper.selectById(1L)).thenReturn(entity);
        List<ResumeSectionEntity> sections = List.of(
                createSection(1L, 1L, "unknownType", "\"some text\"")
        );
        when(resumeSectionMapper.findByResumeId(1L)).thenReturn(sections);

        PolishRequest request = new PolishRequest(null, null, null, 1L, null);
        PolishResponse response = resumeService.polishSection(1L, request);

        assertEquals(1, response.sections().size());
        assertEquals("\"some text\"", response.sections().get(0).polishedText());
        verify(aiClient, never()).chat(any(), any());
        verify(resumeSectionMapper, never()).updateById(any(ResumeSectionEntity.class));
    }

    @Test
    void polishBatchEmptySectionsReturnsEmpty() {
        ResumeEntity entity = createEntity(1L, 1L);
        when(resumeMapper.selectById(1L)).thenReturn(entity);
        when(resumeSectionMapper.findByResumeId(1L)).thenReturn(List.of());

        PolishRequest request = new PolishRequest(null, null, null, 1L, null);
        PolishResponse response = resumeService.polishSection(1L, request);

        assertNotNull(response.sections());
        assertTrue(response.sections().isEmpty());
    }

    @Test
    void polishBatchAiErrorThrowsBadRequest() throws Exception {
        ResumeEntity entity = createEntity(1L, 1L);
        when(resumeMapper.selectById(1L)).thenReturn(entity);
        List<ResumeSectionEntity> sections = List.of(
                createSection(1L, 1L, "summary", "{\"text\":\"7年Java后端经验\"}")
        );
        when(resumeSectionMapper.findByResumeId(1L)).thenReturn(sections);
        when(aiClient.chat(any(), any())).thenThrow(new RuntimeException("API error"));

        PolishRequest request = new PolishRequest(null, null, null, 1L, null);
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> resumeService.polishSection(1L, request));
        assertEquals(ErrorCode.LLM_SERVICE_ERROR.getCode(), ex.getCode());
    }

    // ========== P2-2 补充：polishBatch 异常中断与事务回滚 ==========

    // 测：批量润色时第2节抛异常，第3节绝不执行（循环中断，不执行后续 updateById）
    // 验证 @Transactional 事务回滚行为：异常后循环停止，剩余节不处理
    @Test
    void polishBatch_middleSectionFails_subsequentSectionsSkipped() throws Exception {
        ResumeEntity entity = createEntity(1L, 1L);
        when(resumeMapper.selectById(1L)).thenReturn(entity);
        List<ResumeSectionEntity> sections = List.of(
                createSection(1L, 1L, "summary", "{\"text\":\"7年Java后端经验\"}"),
                createSection(2L, 1L, "education", "{\"school\":\"清华大学\"}"),
                createSection(3L, 1L, "experience", "{\"company\":\"字节跳动\"}")
        );
        when(resumeSectionMapper.findByResumeId(1L)).thenReturn(sections);
        // 第一节成功，第二节抛异常
        when(aiClient.chat(anyString(), anyString()))
                .thenReturn("润色后的概述")
                .thenThrow(new RuntimeException("API timeout on education"));

        PolishRequest request = new PolishRequest(null, null, null, 1L, null);
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> resumeService.polishSection(1L, request));
        assertEquals(ErrorCode.LLM_SERVICE_ERROR.getCode(), ex.getCode());

        // 同一 section 内 aiClient.chat 被调用 2 次（系统提示词 + 内容提示词）
        // 第1次返回成功，第2次抛异常 → 循环中断
        verify(aiClient, times(2)).chat(anyString(), anyString());
        // 仅第1节（异常前）调用了 updateById，后续节被跳过
        verify(resumeSectionMapper, times(1)).updateById(any(ResumeSectionEntity.class));
    }

    // 测：批量润色第1节AI调用成功、updateById 成功，第2节失败 → 抛出异常
    // 在 @Transactional 环境下，第1节的 DB 更新也会被回滚（真实 DB 场景）
    // 单元测试只能验证：异常抛出且循环在失败点中断
    @Test
    void polishBatch_firstSectionSucceeds_secondFails_transactionAborts() throws Exception {
        ResumeEntity entity = createEntity(1L, 1L);
        when(resumeMapper.selectById(1L)).thenReturn(entity);
        List<ResumeSectionEntity> sections = List.of(
                createSection(1L, 1L, "summary", "{\"text\":\"7年Java后端经验\"}"),
                createSection(2L, 1L, "education", "{\"school\":\"清华大学\"}")
        );
        when(resumeSectionMapper.findByResumeId(1L)).thenReturn(sections);
        when(aiClient.chat(anyString(), anyString()))
                .thenReturn("润色后的概述")
                .thenThrow(new RuntimeException("API error"));

        PolishRequest request = new PolishRequest(null, null, null, 1L, null);
        assertThrows(BadRequestException.class,
                () -> resumeService.polishSection(1L, request));

        // 两节都调用了 aiClient.chat
        verify(aiClient, times(2)).chat(anyString(), anyString());
        // 仅第1节 updateById 成功（事务内，但在 mock 下无法验证回滚）
        // 第2节抛异常，没有 updateById
        verify(resumeSectionMapper, times(1)).updateById(any(ResumeSectionEntity.class));
    }

    @Test
    void polishBatchNotFound() {
        when(resumeMapper.selectById(99L)).thenReturn(null);

        PolishRequest request = new PolishRequest(null, null, null, 99L, null);
        assertThrows(NotFoundException.class, () -> resumeService.polishSection(1L, request));
    }

    @Test
    void polishBatchWrongUser() {
        ResumeEntity entity = createEntity(1L, 2L);
        when(resumeMapper.selectById(1L)).thenReturn(entity);

        PolishRequest request = new PolishRequest(null, null, null, 1L, null);
        assertThrows(ForbiddenException.class, () -> resumeService.polishSection(1L, request));
    }

    // ========== create with contentJson ==========

    @Test
    void createWithContentJson() {
        when(resumeMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        ResumeEntity[] captured = new ResumeEntity[1];
        doAnswer(inv -> { ResumeEntity e = inv.getArgument(0); e.setId(3L); captured[0] = e; return 1; })
                .when(resumeMapper).insert(any(ResumeEntity.class));
        when(resumeSectionMapper.insert(any(com.prepdeck.resume.domain.ResumeSectionEntity.class))).thenReturn(1);
        when(resumeMapper.selectById(3L)).thenAnswer(inv -> captured[0]);
        when(resumeSectionMapper.findByResumeId(3L)).thenReturn(List.of());

        ResumeCreateRequest request = new ResumeCreateRequest("resume.json", "content", "{\"skills\":[{\"name\":\"Java\"}]}", null, null, null);
        ResumeVO vo = resumeService.create(1L, request);

        assertNotNull(vo);
        ArgumentCaptor<ResumeEntity> captor = ArgumentCaptor.forClass(ResumeEntity.class);
        verify(resumeMapper).insert(captor.capture());
        assertEquals("{\"skills\":[{\"name\":\"Java\"}]}", captor.getValue().getContentJson());
    }

    // ========== helper ==========

    private ResumeEntity createEntity(Long id, Long userId) {
        ResumeEntity entity = new ResumeEntity();
        entity.setId(id);
        entity.setUserId(userId);
        entity.setFileName("resume.pdf");
        entity.setContentText("sample content");
        entity.setIsActive(true);
        entity.setCreateTime(LocalDateTime.of(2025, 6, 1, 12, 0));
        entity.setUpdateTime(LocalDateTime.of(2025, 6, 15, 12, 0));
        return entity;
    }

    private ResumeSectionEntity createSection(Long id, Long resumeId, String type, String content) {
        ResumeSectionEntity section = new ResumeSectionEntity();
        section.setId(id);
        section.setResumeId(resumeId);
        section.setType(type);
        section.setContent(content);
        section.setSortOrder(0);
        section.setVisible(true);
        return section;
    }
}
