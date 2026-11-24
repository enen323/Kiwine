package com.prepdeck.job.service.impl;

import com.prepdeck.common.enums.ErrorCode;
import com.prepdeck.common.exception.BadRequestException;
import com.prepdeck.common.exception.NotFoundException;
import com.prepdeck.common.model.Result;
import com.prepdeck.common.util.SecurityContext;
import com.prepdeck.job.client.QuotaClient;
import com.prepdeck.job.client.ResumeClient;
import com.prepdeck.job.client.ResumeVO;
import com.prepdeck.job.domain.JobApplicationEntity;
import com.prepdeck.job.dto.request.JobApplicationCreateRequest;
import com.prepdeck.job.dto.request.JobApplicationUpdateRequest;
import com.prepdeck.job.dto.response.JobApplicationPageResponse;
import com.prepdeck.job.dto.response.JobApplicationVO;
import com.prepdeck.job.repository.AiCoverLetterMapper;
import com.prepdeck.job.repository.JobApplicationMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JobApplicationServiceImplTest {

    @Mock JobApplicationMapper jobApplicationMapper;
    @Mock AiCoverLetterMapper aiCoverLetterMapper;
    @Mock com.prepdeck.job.service.AiCoverLetterService aiCoverLetterService;
    @Mock ResumeClient resumeClient;
    @Mock QuotaClient quotaClient;

    private JobApplicationServiceImpl newService() {
        return new JobApplicationServiceImpl(
                jobApplicationMapper, aiCoverLetterMapper, aiCoverLetterService, resumeClient, quotaClient
        );
    }

    // ========== 1. 字段归一化 ==========
    @Test
    void create_normalizesFields() {
        try (var mocked = mockStatic(SecurityContext.class)) {
            mocked.when(SecurityContext::getCurrentUserId).thenReturn(1L);
            when(resumeClient.getResume(anyLong())).thenReturn(Result.success(new ResumeVO(100L, 1L, "My Resume", null)));

            final JobApplicationEntity[] captured = new JobApplicationEntity[1];
            doAnswer(inv -> {
                captured[0] = inv.getArgument(0);
                return 1;
            }).when(jobApplicationMapper).insert(any(JobApplicationEntity.class));
            when(jobApplicationMapper.selectById(any())).thenAnswer(inv -> captured[0]);

            var req = new JobApplicationCreateRequest(
                    "  ByteDance  ", "  Backend Engineer  ", "  APPLIED  ",
                    "  Boss直聘  ", "100", LocalDateTime.now(), "   "
            );

            newService().create(1L, req);
            assertNotNull(captured[0]);
            assertEquals("ByteDance", captured[0].getCompany());
            assertEquals("Backend Engineer", captured[0].getPosition());
            assertEquals("applied", captured[0].getStatus());
            assertEquals("Boss直聘", captured[0].getChannel());
            assertNull(captured[0].getNotes());
        }
    }

    // ========== 2. appliedAt 默认 now；空串 → null ==========
    @Test
    void create_appliedAtDefaultsToNow() {
        try (var mocked = mockStatic(SecurityContext.class)) {
            mocked.when(SecurityContext::getCurrentUserId).thenReturn(1L);
            when(resumeClient.getResume(anyLong())).thenReturn(Result.success(new ResumeVO(100L, 1L, "R", null)));
            LocalDateTime before = LocalDateTime.now();

            final JobApplicationEntity[] captured = new JobApplicationEntity[1];
            doAnswer(inv -> { captured[0] = inv.getArgument(0); return 1; })
                    .when(jobApplicationMapper).insert(any(JobApplicationEntity.class));
            when(jobApplicationMapper.selectById(any())).thenAnswer(inv -> captured[0]);

            newService().create(1L, new JobApplicationCreateRequest("C", "P", "applied", null, null, null, null));
            assertNotNull(captured[0]);
            assertFalse(captured[0].getAppliedAt().isBefore(before.minusSeconds(2)));
        }
    }

    // ========== 3. 非法 status → 400 ==========
    @Test
    void create_invalidStatus_throwsBadRequest() {
        try (var mocked = mockStatic(SecurityContext.class)) {
            mocked.when(SecurityContext::getCurrentUserId).thenReturn(1L);
            assertThrows(BadRequestException.class,
                    () -> newService().create(1L, new JobApplicationCreateRequest("C", "P", "bogus", null, null, null, null)));
            verify(jobApplicationMapper, never()).insert(any(JobApplicationEntity.class));
        }
    }

    // ========== 4. 关联他人简历 → 404 ==========
    @Test
    void create_othersResume_throwsNotFound() {
        try (var mocked = mockStatic(SecurityContext.class)) {
            mocked.when(SecurityContext::getCurrentUserId).thenReturn(1L);
            when(resumeClient.getResume(anyLong())).thenReturn(Result.success(null));
            assertThrows(NotFoundException.class,
                    () -> newService().create(1L, new JobApplicationCreateRequest("C", "P", "applied", null, "100", null, null)));
        }
    }

    // ========== 5. 正常创建 → resumeTitle 回填 ==========
    @Test
    void create_resumeTitlePopulated() {
        try (var mocked = mockStatic(SecurityContext.class)) {
            mocked.when(SecurityContext::getCurrentUserId).thenReturn(1L);
            when(resumeClient.getResume(eq(100L))).thenReturn(Result.success(new ResumeVO(100L, 1L, "My Resume", null)));

            final JobApplicationEntity[] captured = new JobApplicationEntity[1];
            doAnswer(inv -> { captured[0] = inv.getArgument(0); return 1; })
                    .when(jobApplicationMapper).insert(any(JobApplicationEntity.class));
            when(jobApplicationMapper.selectById(any())).thenAnswer(inv -> captured[0]);

            JobApplicationVO result = newService().create(1L,
                    new JobApplicationCreateRequest("C", "P", "applied", null, "100", null, null));
            assertEquals("My Resume", result.resumeTitle());
        }
    }

    // ========== 6. getById 正常返回 ==========
    @Test
    void getById_normal() {
        try (var mocked = mockStatic(SecurityContext.class)) {
            mocked.when(SecurityContext::getCurrentUserId).thenReturn(1L);
            when(jobApplicationMapper.selectById("id-1")).thenReturn(entity(1L, "id-1", "applied"));
            when(aiCoverLetterMapper.selectBatchIds(any())).thenReturn(List.of());

            JobApplicationVO result = newService().getById(1L, "id-1");
            assertEquals("id-1", result.id());
            assertEquals("applied", result.status());
        }
    }

    // ========== 7. getById 不存在 → 404 ==========
    @Test
    void getById_notFound_throwsNotFound() {
        try (var mocked = mockStatic(SecurityContext.class)) {
            mocked.when(SecurityContext::getCurrentUserId).thenReturn(1L);
            when(jobApplicationMapper.selectById("nope")).thenReturn(null);
            assertThrows(NotFoundException.class, () -> newService().getById(1L, "nope"));
        }
    }

    // ========== 8. getById 他人记录 → 404（非 403） ==========
    @Test
    void getById_othersRecord_throwsNotFound() {
        try (var mocked = mockStatic(SecurityContext.class)) {
            mocked.when(SecurityContext::getCurrentUserId).thenReturn(1L);
            when(jobApplicationMapper.selectById("id-2")).thenReturn(entity(2L, "id-2", "applied"));
            assertThrows(NotFoundException.class, () -> newService().getById(1L, "id-2"));
        }
    }

    // ========== 9. 更新字段归一化 ==========
    @Test
    void update_normalizesFields() {
        try (var mocked = mockStatic(SecurityContext.class)) {
            mocked.when(SecurityContext::getCurrentUserId).thenReturn(1L);
            var existing = entity(1L, "id-1", "applied");
            existing.setChannel("old");
            when(jobApplicationMapper.selectById("id-1")).thenReturn(existing);
            when(resumeClient.getResume(anyLong())).thenReturn(Result.success(new ResumeVO(100L, 1L, "R", null)));
            when(jobApplicationMapper.updateById(any(JobApplicationEntity.class))).thenReturn(1);

            newService().update(1L, "id-1",
                    new JobApplicationUpdateRequest("  新公司  ", "  新职位  ", "  INTERVIEWING  ",
                            "  Boss直聘  ", null, LocalDateTime.now(), " 备注 "));
            assertEquals("新公司", existing.getCompany());
            assertEquals("interviewing", existing.getStatus());
            assertEquals("Boss直聘", existing.getChannel());
        }
    }

    // ========== 10. 更新关联他人简历 → 404 ==========
    @Test
    void update_othersResume_throwsNotFound() {
        try (var mocked = mockStatic(SecurityContext.class)) {
            mocked.when(SecurityContext::getCurrentUserId).thenReturn(1L);
            var existing = entity(1L, "id-1", "applied");
            when(jobApplicationMapper.selectById("id-1")).thenReturn(existing);
            when(resumeClient.getResume(anyLong())).thenReturn(Result.success(null));
            assertThrows(NotFoundException.class,
                    () -> newService().update(1L, "id-1", new JobApplicationUpdateRequest("C", "P", "applied", null, "200", null, null)));
        }
    }

    // ========== 11. appliedAt 传 null → 保留原值 ==========
    @Test
    void update_appliedAtNull_preservesOriginal() {
        try (var mocked = mockStatic(SecurityContext.class)) {
            mocked.when(SecurityContext::getCurrentUserId).thenReturn(1L);
            var existing = entity(1L, "id-1", "applied");
            existing.setAppliedAt(LocalDateTime.of(2024, 1, 1, 0, 0));
            when(jobApplicationMapper.selectById("id-1")).thenReturn(existing);
            when(resumeClient.getResume(anyLong())).thenReturn(Result.success(new ResumeVO(100L, 1L, "R", null)));
            when(jobApplicationMapper.updateById(any(JobApplicationEntity.class))).thenReturn(1);

            newService().update(1L, "id-1",
                    new JobApplicationUpdateRequest("C", "P", "applied", null, null, null, null));
            assertEquals(LocalDateTime.of(2024, 1, 1, 0, 0), existing.getAppliedAt());
        }
    }

    // ========== 12. status 非法 → 400 ==========
    @Test
    void update_invalidStatus_throwsBadRequest() {
        try (var mocked = mockStatic(SecurityContext.class)) {
            mocked.when(SecurityContext::getCurrentUserId).thenReturn(1L);
            var existing = entity(1L, "id-1", "applied");
            when(jobApplicationMapper.selectById("id-1")).thenReturn(existing);
            assertThrows(BadRequestException.class,
                    () -> newService().update(1L, "id-1", new JobApplicationUpdateRequest("C", "P", "bogus", null, null, null, null)));
            verify(jobApplicationMapper, never()).updateById(any(JobApplicationEntity.class));
        }
    }

    // ========== 13. 物理删除 ==========
    @Test
    void delete_physicalDelete() {
        try (var mocked = mockStatic(SecurityContext.class)) {
            mocked.when(SecurityContext::getCurrentUserId).thenReturn(1L);
            when(jobApplicationMapper.selectById("id-1")).thenReturn(entity(1L, "id-1", "applied"));

            newService().delete(1L, "id-1");
            verify(jobApplicationMapper).deleteById("id-1");
        }
    }

    // ========== 14. 删除他人记录 → 404 ==========
    @Test
    void delete_othersRecord_throwsNotFound() {
        try (var mocked = mockStatic(SecurityContext.class)) {
            mocked.when(SecurityContext::getCurrentUserId).thenReturn(1L);
            when(jobApplicationMapper.selectById("id-1")).thenReturn(entity(2L, "id-1", "applied"));
            assertThrows(NotFoundException.class, () -> newService().delete(1L, "id-1"));
            verify(jobApplicationMapper, never()).deleteById(any(String.class));
        }
    }

    // ========== 15. 分页参数修正 ==========
    @Test
    void list_pageBoundsClamped() {
        try (var mocked = mockStatic(SecurityContext.class)) {
            mocked.when(SecurityContext::getCurrentUserId).thenReturn(1L);

            var page = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<JobApplicationEntity>();
            page.setRecords(List.of());
            page.setTotal(0L);
            page.setSize(1);

            var stubMapper = mock(JobApplicationMapper.class);
            doReturn(page).when(stubMapper).findPageByUserId(any(), anyLong(), any(), any());

            JobApplicationServiceImpl service = new JobApplicationServiceImpl(
                    stubMapper, aiCoverLetterMapper, aiCoverLetterService, resumeClient, quotaClient
            );

            var r1 = service.list(1L, null, null, 0, 0);
            assertEquals(1, r1.page());
            assertEquals(1, r1.pageSize());

            var r2 = service.list(1L, null, null, 1, 999999);
            assertEquals(1, r2.page());
            assertEquals(100, r2.pageSize());
        }
    }

    // ========== 16. status 非法 → 400，不执行查询 ==========
    @Test
    void list_invalidStatus_throwsBadRequest() {
        try (var mocked = mockStatic(SecurityContext.class)) {
            mocked.when(SecurityContext::getCurrentUserId).thenReturn(1L);
            assertThrows(BadRequestException.class, () -> newService().list(1L, "bogus", null, 1, 10));
            verify(jobApplicationMapper, never()).selectPage(any(), any());
        }
    }

    // ========== Helper ==========
    private static JobApplicationEntity entity(Long userId, String id, String status) {
        JobApplicationEntity e = new JobApplicationEntity();
        e.setUserId(userId);
        e.setId(id);
        e.setCompany("Corp");
        e.setPosition("Pos");
        e.setStatus(status);
        e.setAppliedAt(LocalDateTime.of(2025, 1, 1, 0, 0));
        e.setCreateTime(LocalDateTime.of(2025, 1, 1, 0, 0));
        e.setUpdateTime(LocalDateTime.of(2025, 1, 1, 0, 0));
        return e;
    }
}
