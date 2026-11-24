package com.prepdeck.job.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.prepdeck.common.ai.AiClient;
import com.prepdeck.common.dto.QuotaCheckResult;
import com.prepdeck.common.enums.ErrorCode;
import com.prepdeck.common.exception.BadRequestException;
import com.prepdeck.common.exception.BusinessException;
import com.prepdeck.common.exception.NotFoundException;
import com.prepdeck.common.model.Result;
import com.prepdeck.common.util.SecurityContext;
import com.prepdeck.job.client.QuotaClient;
import com.prepdeck.job.client.ResumeClient;
import com.prepdeck.job.client.ResumeVO;
import com.prepdeck.job.domain.ApplicationStatus;
import com.prepdeck.job.domain.AiCoverLetterEntity;
import com.prepdeck.job.domain.JobApplicationEntity;
import com.prepdeck.job.dto.request.AiCoverLetterRequest;
import com.prepdeck.job.dto.response.AiCoverLetterVO;
import com.prepdeck.job.repository.AiCoverLetterMapper;
import com.prepdeck.job.repository.JobApplicationMapper;
import com.prepdeck.job.service.AiCoverLetterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class AiCoverLetterServiceImpl implements AiCoverLetterService {

    private static final String SYSTEM_PROMPT = """
            You are a professional cover letter writing assistant.
            Write concise, polished, application-ready cover letters from verified resume content.

            Rules:
            - Output valid JSON only.
            - The JSON must contain title and body.
            - Body should be a complete cover letter in plain text with natural paragraphs.
            - Use the selected output language for title and body.
            - Keep the tone professional, confident, and concise.
            - Tailor the letter to the target company, position, job description, and user notes when provided.
            - Use only facts supported by the resume or user-provided target context; do not invent employers, degrees, dates, metrics, or credentials.
            - Do not include Markdown fences, commentary, or placeholders.
            """;

    private final AiCoverLetterMapper aiCoverLetterMapper;
    private final JobApplicationMapper jobApplicationMapper;
    private final AiClient aiClient;
    private final ResumeClient resumeClient;
    private final QuotaClient quotaClient;

    @Override
    public AiCoverLetterVO generate(Long userId, String applicationId, AiCoverLetterRequest request) {
        // 路径参数是投递记录 ID（见 JobApplicationController#generateCoverLetter），
        // 求职信需基于该投递关联的简历内容生成，因此先取出真实 resumeId。
        JobApplicationEntity application = requireOwnedApplication(applicationId, userId);
        String resumeId = application.getResumeId();
        if (resumeId == null || resumeId.isBlank()) {
            throw new BadRequestException("该投递记录未关联简历，无法生成求职信");
        }
        validateResumeOwnership(resumeId, userId);

        Result<QuotaCheckResult> quotaResult = quotaClient.consume(userId, "COVER_LETTER");
        if (quotaResult == null || quotaResult.data() == null || !quotaResult.data().allowed()) {
            throw new BusinessException(
                    ErrorCode.QUOTA_EXHAUSTED.getCode(),
                    quotaResult != null && quotaResult.data() != null
                            ? quotaResult.data().reason() : "本月免费额度已用完");
        }

        ApplicationStatus statusEnum = ApplicationStatus.parse(request.outputLanguage());
        String outputLanguage = statusEnum != null ? statusEnum.getCode() : request.outputLanguage().trim();

        String userMessage = buildUserMessage(userId, resumeId, request, outputLanguage);

        String jsonResponse = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                jsonResponse = aiClient.chat(SYSTEM_PROMPT, userMessage + "\n\nRespond with valid JSON containing only: {\"title\": \"...\", \"body\": \"...\"}");
                break;
            } catch (Exception e) {
                log.warn("AI cover letter attempt {} failed: userId={}, resumeId={}", attempt, userId, resumeId, e);
            }
        }

        if (jsonResponse == null) {
            throw new BusinessException(ErrorCode.LLM_SERVICE_ERROR.getCode(), "AI 生成求职信失败，请稍后重试");
        }

        String title = null;
        String body = null;
        try {
            com.fasterxml.jackson.databind.JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(jsonResponse);
            title = root.path("title").asText(null);
            body = root.path("body").asText(null);
        } catch (Exception e) {
            log.error("Failed to parse AI cover letter response: userId={}", userId, e);
            throw new BusinessException(ErrorCode.LLM_SERVICE_ERROR.getCode(), "AI 生成求职信失败，请稍后重试");
        }

        if (title == null || title.isBlank() || body == null || body.isBlank()) {
            throw new BusinessException(ErrorCode.LLM_SERVICE_ERROR.getCode(), "AI 生成求职信内容为空，请重试");
        }

        AiCoverLetterEntity entity = new AiCoverLetterEntity();
        entity.setId(java.util.UUID.randomUUID().toString());
        entity.setUserId(userId);
        entity.setResumeId(resumeId);
        entity.setApplicationId(applicationId);
        entity.setCompany(request.company().trim());
        entity.setPosition(request.position().trim());
        entity.setJobDescription(trimOrNull(request.jobDescription()));
        entity.setExtraNotes(trimOrNull(request.extraNotes()));
        entity.setOutputLanguage(outputLanguage);
        entity.setTitle(title.trim());
        entity.setBody(body.trim());

        aiCoverLetterMapper.insert(entity);
        log.info("AiCoverLetter generated: userId={}, id={}", userId, entity.getId());

        return toResponse(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AiCoverLetterVO> listByResumeId(Long userId, String resumeId) {
        validateResumeOwnership(resumeId, userId);

        List<AiCoverLetterEntity> entities = aiCoverLetterMapper.findByResumeId(userId, resumeId);
        return entities.stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AiCoverLetterVO getById(Long userId, String coverLetterId) {
        AiCoverLetterEntity entity = requireOwned(coverLetterId, userId);
        return toResponse(entity);
    }

    @Override
    public AiCoverLetterVO update(Long userId, String coverLetterId, AiCoverLetterRequest request) {
        AiCoverLetterEntity entity = requireOwned(coverLetterId, userId);

        entity.setCompany(request.company().trim());
        entity.setPosition(request.position().trim());
        entity.setJobDescription(trimOrNull(request.jobDescription()));
        entity.setExtraNotes(trimOrNull(request.extraNotes()));
        entity.setOutputLanguage(request.outputLanguage().trim());

        aiCoverLetterMapper.updateById(entity);
        log.info("AiCoverLetter updated: userId={}, id={}", userId, coverLetterId);

        return toResponse(entity);
    }

    @Override
    public void delete(Long userId, String coverLetterId) {
        AiCoverLetterEntity entity = requireOwned(coverLetterId, userId);
        aiCoverLetterMapper.deleteById(entity.getId());
        log.info("AiCoverLetter deleted: userId={}, id={}", userId, coverLetterId);
    }

    private JobApplicationEntity requireOwnedApplication(String applicationId, Long userId) {
        if (applicationId == null || applicationId.isBlank()) {
            throw new BadRequestException(ErrorCode.PARAM_INVALID);
        }
        JobApplicationEntity application = jobApplicationMapper.selectById(applicationId);
        if (application == null || !userId.equals(application.getUserId())) {
            throw new NotFoundException("投递记录不存在");
        }
        return application;
    }

    private void validateResumeOwnership(String resumeId, Long userId) {
        Result<ResumeVO> result = resumeClient.getResume(Long.valueOf(resumeId));
        if (result == null || result.code() != 0 || result.data() == null) {
            throw new NotFoundException(ErrorCode.RESUME_NOT_FOUND);
        }
        // 跨服务无法自动核验归属，此处显式校验，防止越权用他人简历生成求职信。
        // 归属不符时同样以「简历不存在」应答，避免泄露简历存在性。
        if (!userId.equals(result.data().userId())) {
            throw new NotFoundException(ErrorCode.RESUME_NOT_FOUND);
        }
    }

    private AiCoverLetterEntity requireOwned(String id, Long userId) {
        AiCoverLetterEntity entity = aiCoverLetterMapper.selectById(id);
        if (entity == null || !userId.equals(entity.getUserId())) {
            throw new NotFoundException(ErrorCode.USER_NOT_FOUND);
        }
        return entity;
    }

    private String buildUserMessage(Long userId, String resumeId, AiCoverLetterRequest request, String outputLanguage) {
        StringBuilder sb = new StringBuilder();
        sb.append("Target company: ").append(request.company()).append("\n");
        sb.append("Target position: ").append(request.position()).append("\n");
        sb.append("Output language: ").append(outputLanguage).append("\n");

        if (request.jobDescription() != null && !request.jobDescription().isBlank()) {
            sb.append("\nTarget job description:\n").append(request.jobDescription()).append("\n");
        }

        if (request.extraNotes() != null && !request.extraNotes().isBlank()) {
            sb.append("\nExtra user notes:\n").append(request.extraNotes()).append("\n");
        }

        String applicationId = trimOrNull(request.applicationId());
        if (applicationId != null) {
            JobApplicationEntity app = jobApplicationMapper.selectById(applicationId);
            if (app != null && userId.equals(app.getUserId())) {
                sb.append("\nLinked application context:\n");
                sb.append("Status: ").append(app.getStatus()).append("\n");
                sb.append("Channel: ").append(app.getChannel() != null ? app.getChannel() : "N/A").append("\n");
                sb.append("Application notes: ").append(app.getNotes() != null ? app.getNotes() : "N/A").append("\n");
            }
        }

        sb.append("\nResume content JSON:\n");
        Result<ResumeVO> resumeResult = resumeClient.getResume(Long.valueOf(resumeId));
        if (resumeResult != null && resumeResult.data() != null) {
            sb.append("{");
            sb.append("\"title\": \"").append(escapeJson(resumeResult.data().title())).append("\",");
            sb.append("\"contentText\": \"").append(escapeJson(resumeResult.data().contentText())).append("\"");
            sb.append("}");
        } else {
            sb.append("{}");
        }

        return sb.toString();
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }

    private String trimOrNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private AiCoverLetterVO toResponse(AiCoverLetterEntity entity) {
        return new AiCoverLetterVO(
                entity.getId(),
                entity.getResumeId(),
                entity.getApplicationId(),
                entity.getCompany(),
                entity.getPosition(),
                entity.getJobDescription(),
                entity.getExtraNotes(),
                entity.getOutputLanguage(),
                entity.getTitle(),
                entity.getBody(),
                entity.getCreateTime(),
                entity.getUpdateTime()
        );
    }
}
