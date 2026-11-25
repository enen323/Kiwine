package com.prepdeck.job.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.prepdeck.common.ai.AiClient;
import com.prepdeck.common.enums.ErrorCode;
import com.prepdeck.common.exception.BadRequestException;
import com.prepdeck.common.exception.BusinessException;
import com.prepdeck.common.exception.NotFoundException;
import com.prepdeck.common.model.Result;
import com.prepdeck.common.util.SecurityContext;
import com.prepdeck.job.client.QuotaClient;
import com.prepdeck.job.client.ResumeClient;
import com.prepdeck.job.dto.response.ResumeVO;
import com.prepdeck.job.domain.ApplicationStatus;
import com.prepdeck.job.domain.AiCoverLetterEntity;
import com.prepdeck.job.domain.JobApplicationEntity;
import com.prepdeck.job.dto.request.AiCoverLetterRequest;
import com.prepdeck.job.dto.request.JobApplicationCreateRequest;
import com.prepdeck.job.dto.request.JobApplicationUpdateRequest;
import com.prepdeck.job.dto.response.AiCoverLetterVO;
import com.prepdeck.job.dto.response.JobApplicationPageResponse;
import com.prepdeck.job.dto.response.JobApplicationVO;
import com.prepdeck.job.repository.AiCoverLetterMapper;
import com.prepdeck.job.repository.JobApplicationMapper;
import com.prepdeck.job.service.AiCoverLetterService;
import com.prepdeck.job.service.JobApplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class JobApplicationServiceImpl implements JobApplicationService {

    private final JobApplicationMapper jobApplicationMapper;
    private final AiCoverLetterMapper aiCoverLetterMapper;
    private final AiCoverLetterService aiCoverLetterService;
    private final ResumeClient resumeClient;
    private final QuotaClient quotaClient;

    @Override
    public JobApplicationVO create(Long userId, JobApplicationCreateRequest request) {
        validateStatus(request.status());

        ApplicationStatus statusEnum = ApplicationStatus.parse(request.status());
        String normalizedStatus = statusEnum != null ? statusEnum.getCode() : request.status().trim().toLowerCase();

        JobApplicationEntity entity = new JobApplicationEntity();
        entity.setId(java.util.UUID.randomUUID().toString());
        entity.setUserId(userId);
        entity.setCompany(request.company().trim());
        entity.setPosition(request.position().trim());
        entity.setStatus(normalizedStatus);
        entity.setChannel(trimOrNull(request.channel()));
        entity.setResumeId(validateOwnedResumeId(request.resumeId(), userId));
        entity.setAppliedAt(request.appliedAt() != null ? request.appliedAt() : LocalDateTime.now());
        entity.setNotes(trimOrNull(request.notes()));

        jobApplicationMapper.insert(entity);
        log.info("JobApplication created: userId={}, id={}", userId, entity.getId());

        return toResponse(entity, fetchResumeTitle(entity.getResumeId(), userId));
    }

    @Override
    public JobApplicationVO update(Long userId, String id, JobApplicationUpdateRequest request) {
        validateStatus(request.status());

        JobApplicationEntity entity = requireOwned(id, userId);
        entity.setCompany(request.company().trim());
        entity.setPosition(request.position().trim());

        ApplicationStatus statusEnum = ApplicationStatus.parse(request.status());
        String normalizedStatus = statusEnum != null ? statusEnum.getCode() : request.status().trim().toLowerCase();
        entity.setStatus(normalizedStatus);

        entity.setChannel(trimOrNull(request.channel()));
        entity.setResumeId(validateOwnedResumeId(request.resumeId(), userId));

        if (request.appliedAt() != null) {
            entity.setAppliedAt(request.appliedAt());
        }

        entity.setNotes(trimOrNull(request.notes()));

        jobApplicationMapper.updateById(entity);
        log.info("JobApplication updated: userId={}, id={}", userId, id);

        return toResponse(entity, fetchResumeTitle(entity.getResumeId(), userId));
    }

    @Override
    @Transactional(readOnly = true)
    public JobApplicationVO getById(Long userId, String id) {
        JobApplicationEntity entity = requireOwned(id, userId);
        String resumeTitle = fetchResumeTitle(entity.getResumeId(), userId);
        return toResponse(entity, resumeTitle);
    }

    @Override
    public void delete(Long userId, String id) {
        JobApplicationEntity entity = requireOwned(id, userId);
        jobApplicationMapper.deleteById(entity.getId());
        log.info("JobApplication deleted: userId={}, id={}", userId, id);
    }

    @Override
    @Transactional(readOnly = true)
    public JobApplicationPageResponse list(Long userId, String status, String keyword, int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safePageSize = Math.min(Math.max(1, pageSize), 100);

        if (status != null && !status.isBlank()) {
            ApplicationStatus statusEnum = ApplicationStatus.parse(status);
            if (statusEnum == null) {
                throw new BadRequestException(ErrorCode.PARAM_INVALID);
            }
            status = statusEnum.getCode();
        }

        String escapedKeyword = null;
        if (keyword != null && !keyword.isBlank()) {
            escapedKeyword = escapeLikeKeyword(keyword.trim().toLowerCase());
        }

        Page<JobApplicationEntity> mpPage = new Page<>(safePage, safePageSize);
        Page<JobApplicationEntity> resultPage = jobApplicationMapper.findPageByUserId(mpPage, userId, status, escapedKeyword);

        List<JobApplicationEntity> records = resultPage.getRecords();
        List<String> resumeIds = records.stream()
                .map(JobApplicationEntity::getResumeId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();

        Map<String, String> titleMap = fetchResumeTitles(resumeIds, userId);

        List<JobApplicationVO> items = records.stream()
                .map(e -> toResponse(e, titleMap.get(e.getResumeId())))
                .toList();

        long total = resultPage.getTotal();
        int totalPages = (int) Math.max(1, Math.ceil((double) total / safePageSize));

        return new JobApplicationPageResponse(items, total, safePage, safePageSize, totalPages);
    }

    private JobApplicationEntity requireOwned(String id, Long userId) {
        JobApplicationEntity entity = jobApplicationMapper.selectById(id);
        if (entity == null || !userId.equals(entity.getUserId())) {
            throw new NotFoundException(ErrorCode.USER_NOT_FOUND);
        }
        return entity;
    }

    private void validateStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            throw new BadRequestException(ErrorCode.PARAM_INVALID);
        }
        if (ApplicationStatus.parse(rawStatus) == null) {
            throw new BadRequestException(ErrorCode.PARAM_INVALID);
        }
    }

    private String validateOwnedResumeId(String resumeId, Long userId) {
        if (resumeId == null || resumeId.isBlank()) {
            return null;
        }
        Result<ResumeVO> result = resumeClient.getResume(Long.valueOf(resumeId));
        if (result == null || result.code() != 0 || result.data() == null) {
            throw new NotFoundException(ErrorCode.RESUME_NOT_FOUND);
        }
        return resumeId;
    }

    private String trimOrNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String escapeLikeKeyword(String keyword) {
        StringBuilder sb = new StringBuilder();
        for (char c : keyword.toCharArray()) {
            if (c == '%' || c == '_') {
                sb.append('\\');
            }
            sb.append(c);
        }
        return "%" + sb + "%";
    }

    private String fetchResumeTitle(String resumeId, Long userId) {
        if (resumeId == null) {
            return null;
        }
        Map<String, String> map = fetchResumeTitles(List.of(resumeId), userId);
        return map.get(resumeId);
    }

    private Map<String, String> fetchResumeTitles(List<String> resumeIds, Long userId) {
        if (resumeIds.isEmpty()) {
            return Map.of();
        }
        return resumeIds.stream()
                .map(id -> {
                    try {
                        Result<ResumeVO> result = resumeClient.getResume(Long.valueOf(id));
                        if (result != null && result.data() != null) {
                            return Map.entry(id, result.data().title());
                        }
                    } catch (Exception e) {
                        log.warn("Failed to fetch resume title: resumeId={}", id, e);
                    }
                    return null;
                })
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a));
    }

    private JobApplicationVO toResponse(JobApplicationEntity entity, String resumeTitle) {
        return new JobApplicationVO(
                entity.getId(),
                entity.getCompany(),
                entity.getPosition(),
                entity.getStatus(),
                entity.getChannel(),
                entity.getResumeId(),
                resumeTitle,
                entity.getAppliedAt(),
                entity.getNotes(),
                entity.getCreateTime(),
                entity.getUpdateTime()
        );
    }
}
