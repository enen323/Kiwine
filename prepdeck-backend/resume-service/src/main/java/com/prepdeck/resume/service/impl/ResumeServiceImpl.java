package com.prepdeck.resume.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.prepdeck.common.ai.AiClient;
import com.prepdeck.common.dto.QuotaCheckResult;
import com.prepdeck.common.enums.ErrorCode;
import com.prepdeck.common.exception.BadRequestException;
import com.prepdeck.common.exception.BusinessException;
import com.prepdeck.common.exception.ForbiddenException;
import com.prepdeck.common.exception.NotFoundException;
import com.prepdeck.common.model.PageResult;
import com.prepdeck.common.model.Result;
import com.prepdeck.resume.client.QuotaClient;
import com.prepdeck.resume.domain.ResumeEntity;
import com.prepdeck.resume.domain.ResumeSectionEntity;
import com.prepdeck.resume.dto.request.AiChatRequest;
import com.prepdeck.resume.dto.request.PolishRequest;
import com.prepdeck.resume.dto.request.ResumeCreateRequest;
import com.prepdeck.resume.dto.request.ResumeUpdateRequest;
import com.prepdeck.resume.dto.request.SectionRequest;
import com.prepdeck.resume.dto.response.AiChatResponse;
import com.prepdeck.resume.dto.response.PolishResponse;
import com.prepdeck.resume.dto.response.ResumeVO;
import com.prepdeck.resume.dto.response.SectionVO;
import com.prepdeck.resume.repository.ResumeMapper;
import com.prepdeck.resume.repository.ResumeSectionMapper;
import com.prepdeck.resume.service.ResumeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 简历服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class ResumeServiceImpl implements ResumeService {

    private final ResumeMapper resumeMapper;
    private final ResumeSectionMapper resumeSectionMapper;
    private final AiClient aiClient;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final QuotaClient quotaClient;

    @Value("${resume.upload.dir:./uploads/resume}")
    private String uploadDir;

    // ponytail: single prompt drives whole AI chat flow; split into specialized sub-prompts if per-section tuning needed
    private static final int MAX_FOLLOW_UP_ROUNDS = 12;

    private static final String AI_CHAT_SYSTEM_PROMPT = """
你是一位专业的 AI 简历助手。你的任务是通过对话帮助用户挖掘自身优势并创建简历。

你必须严格按照以下 JSON 格式回复，不要包含任何其他内容。

如果还需要更多信息，回复格式：
{"type":"question","content":"你的追问","options":["选项1","选项2","选项3"]}

如果已收集足够信息，生成简历并回复（必须包含所有以下 section type）：
{"type":"complete","title":"简历标题","sections":[
  {"type":"personalInfo","title":"个人信息","content":"描述内容","sortOrder":0},
  {"type":"summary","title":"个人概述","content":"描述内容","sortOrder":1},
  {"type":"education","title":"教育经历","content":"描述内容","sortOrder":2},
  {"type":"experience","title":"工作经历","content":"描述内容","sortOrder":3},
  {"type":"project","title":"项目经历","content":"描述内容","sortOrder":4},
  {"type":"skills","title":"专业技能","content":"描述内容","sortOrder":5}
]}

可选额外 section type：language, certification, publication, custom
content 字段说明：
- personalInfo: 如果对话中未提及姓名、联系方式等，使用占位文本如"请填写姓名""请填写电话"
- skills: content 必须为如下 JSON 格式字符串：{"categories":[{"id":"唯一标识","name":"分类名称","skills":["技能1","技能2"]}]}
- 其他类型: content 为普通文本描述

规则：
- options 必须是实质性的回答选项，如具体技能、具体方向、具体成果。禁止出现"好的，我提供"、"是的"等无意义占位选项。
- 不要问可直接查到的个人信息（姓名、学校、专业、毕业时间、联系方式等）。聚焦用户的实际经历、项目贡献、技能亮点。
- 追问要挖掘用户的优势、量化成果、技术深度、解决问题能力。例如问"你负责的模块带来了什么可量化的提升？"而非"你什么时候毕业的？"
- complete 时必须输出全部 6 个基础 section，不允许遗漏。
""";

    // ponytail: 8 sectionTypes; extract to template registry if more added in Phase 2
    private static final List<String> REQUIRED_SECTION_TYPES = List.of(
            "personalInfo", "summary", "education", "experience", "project", "skills"
    );

    private static final Map<String, String> DEFAULT_SECTION_CONTENT = Map.ofEntries(
            Map.entry("personalInfo", "{\"name\":\"请填写姓名\",\"phone\":\"请填写电话\",\"email\":\"请填写邮箱\"}"),
            Map.entry("summary", "请填写个人概述"),
            Map.entry("education", "请填写教育经历"),
            Map.entry("experience", "请填写工作经历"),
            Map.entry("project", "请填写项目经历"),
            Map.entry("skills", "{\"categories\":[]}")
    );

    private static final Map<String, String> SECTION_DEFAULT_TITLES = Map.ofEntries(
            Map.entry("personalInfo", "个人信息"),
            Map.entry("summary", "个人概述"),
            Map.entry("education", "教育经历"),
            Map.entry("experience", "工作经历"),
            Map.entry("project", "项目经历"),
            Map.entry("skills", "专业技能")
    );

    private static final Map<String, String> SECTION_PROMPTS = Map.ofEntries(
            Map.entry("summary",
            "你是一位专业的简历助手。请将用户输入的个人简介关键词整理成简洁、通顺、有吸引力的个人概述。" +
            "突出核心竞争力和职业目标。直接输出润色结果，不要加任何额外说明。"),

            Map.entry("education",
            "你是一位专业的简历助手。请将用户输入的教育经历关键词整理成通顺的中文描述。" +
            "格式要求：学校 + 专业 + 学历 + 时间。保持简洁专业。直接输出润色结果。"),

            Map.entry("experience",
            "你是一位专业的简历助手。请将用户输入的工作经历关键词改写成通顺、专业的中文工作描述。" +
            "使用倒金字塔结构：公司 → 职位 → 职责 → 成果。不要添加虚构信息。直接输出润色结果。"),

            Map.entry("project",
            "你是一位专业的简历助手。请将用户输入的项目经历关键词改写成STAR风格的描述。" +
            "包含：项目背景、技术架构、你在其中的角色和贡献。不要添加虚构信息。直接输出润色结果。"),

            Map.entry("skills",
            "你是一位专业的简历助手。请将用户输入的专业技能关键词整理成 JSON 格式的 categories 结构。" +
            "格式：{\"categories\":[{\"id\":\"唯一标识\",\"name\":\"分类名称\",\"skills\":[\"技能1\",\"技能2\"]}]}" +
            "直接输出 JSON 结果，不要加任何额外说明。"),

            Map.entry("language",
            "你是一位专业的简历助手。请将用户输入的语言能力关键词整理成通顺的描述。" +
            "格式：语言名称 + 熟练程度。直接输出润色结果。"),

            Map.entry("publication",
            "你是一位专业的简历助手。请将用户输入的发表物关键词整理成规范的描述。" +
            "格式：标题 + 发表平台 + 时间 + 角色。直接输出润色结果。"),

            Map.entry("custom",
            "你是一位专业的简历助手。请将用户输入的自定义内容改写成通顺、专业的描述。" +
            "保持原有核心信息，优化表达方式。直接输出润色结果。")
    );

    // ponytail: paired with SECTION_PROMPTS, extract together if more sectionTypes added
    private static final Map<String, String> SECTION_EXAMPLES = Map.ofEntries(
            Map.entry("summary",
            "7年Java后端开发经验的高级工程师，擅长微服务架构设计与高并发系统优化，具备大型电商平台从0到1的搭建经验"),

            Map.entry("education",
            "北京大学 计算机科学与技术 硕士 2019.09-2022.06"),

            Map.entry("experience",
            "字节跳动 | 后端开发工程师 | 2022.07-至今 | 负责电商平台订单系统的设计与开发，日均处理订单量100万+，系统可用性99.99%"),

            Map.entry("project",
            "电商平台 | 后端负责人 | 使用Spring Cloud Alibaba构建微服务架构，负责订单、支付模块的设计与实现，日均PV 500万+"),

            Map.entry("skills",
            "{\"categories\":[{\"id\":\"c1\",\"name\":\"专业技能\",\"skills\":[\"Java\",\"Spring Boot\",\"MyBatis\",\"MySQL\"]}]}"),

            Map.entry("language",
            "英语（流利，CET-6 550分），日语（基础，日常会话水平）"),

            Map.entry("publication",
            "《基于微服务架构的电商平台设计与实现》| 《计算机工程》| 2023.05 | 第一作者"),

            Map.entry("custom",
            "拥有丰富的跨团队协作经验，擅长技术方案评审与代码审查，推动团队代码质量提升30%")
    );

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024L; // 10 MB
    private static final String PDF_CONTENT_TYPE = "application/pdf";

    @Override
    public ResumeVO create(Long userId, ResumeCreateRequest request) {
        ResumeEntity entity = new ResumeEntity();
        entity.setUserId(userId);
        entity.setTitle(request.title());
        entity.setFileName(request.fileName());
        entity.setContentText(request.contentText());
        if (request.contentJson() != null) {
            entity.setContentJson(request.contentJson());
        }

        boolean isFirst = resumeMapper.selectCount(
                new LambdaQueryWrapper<ResumeEntity>()
                        .eq(ResumeEntity::getUserId, userId)
        ) == 0;
        entity.setIsActive(isFirst);

        resumeMapper.insert(entity);

        if (request.sections() != null && !request.sections().isEmpty()) {
            syncSections(entity.getId(), request.sections());
        } else {
            createDefaultSections(entity.getId());
        }

        if (request.sessionId() != null) {
            stringRedisTemplate.delete("resume:ai:completed:" + request.sessionId());
        }

        log.info("Resume created: userId={}, resumeId={}", userId, entity.getId());
        return getByIdWithSections(entity.getId(), userId);
    }

    private void createDefaultSections(Long resumeId) {
        var defaultTypes = List.of("personalInfo", "summary", "education", "experience", "project", "skills");
        for (int i = 0; i < defaultTypes.size(); i++) {
            ResumeSectionEntity section = new ResumeSectionEntity();
            section.setResumeId(resumeId);
            section.setType(defaultTypes.get(i));
            section.setTitle(defaultTypes.get(i));
            section.setSortOrder(i);
            section.setVisible(true);
            section.setContent("{}");
            resumeSectionMapper.insert(section);
        }
    }

    @Override
    public ResumeVO update(Long id, Long userId, ResumeUpdateRequest request) {
        ResumeEntity entity = getOwnedEntity(id, userId);

        if (request.title() != null) entity.setTitle(request.title());
        if (request.template() != null) entity.setTemplate(request.template());
        if (request.language() != null) entity.setLanguage(request.language());
        if (request.themeConfig() != null) {
            entity.setThemeConfig(
                request.themeConfig() instanceof String s
                    ? s
                    : objectMapper.valueToTree(request.themeConfig()).toString()
            );
        }
        if (request.isPublic() != null) entity.setIsPublic(request.isPublic());
        if (request.fileName() != null) entity.setFileName(request.fileName());
        if (request.contentText() != null) entity.setContentText(request.contentText());
        if (request.isActive() != null) entity.setIsActive(request.isActive());
        if (request.contentJson() != null) entity.setContentJson(request.contentJson());

        resumeMapper.updateById(entity);

        if (request.sections() != null) {
            syncSections(id, request.sections());
        }

        log.info("Resume updated: id={}, userId={}", id, userId);
        return getByIdWithSections(id, userId);
    }

    private void syncSections(Long resumeId, List<SectionRequest> sections) {
        List<ResumeSectionEntity> existing = resumeSectionMapper.findByResumeId(resumeId);
        Set<Long> incomingIds = new HashSet<>();

        for (int i = 0; i < sections.size(); i++) {
            SectionRequest req = sections.get(i);
            String content = toJsonContent(req.content());
            if (req.id() != null) {
                incomingIds.add(req.id());
                ResumeSectionEntity section = existing.stream()
                        .filter(s -> s.getId().equals(req.id()))
                        .findFirst().orElse(null);
                if (section != null) {
                    section.setType(req.type());
                    section.setTitle(req.title());
                    section.setSortOrder(i);
                    section.setVisible(req.visible() != null ? req.visible() : true);
                    section.setContent(content);
                    resumeSectionMapper.updateById(section);
                    continue;
                }
            }
            ResumeSectionEntity section = new ResumeSectionEntity();
            section.setResumeId(resumeId);
            section.setType(req.type());
            section.setTitle(req.title());
            section.setSortOrder(i);
            section.setVisible(req.visible() != null ? req.visible() : true);
            section.setContent(content);
            resumeSectionMapper.insert(section);
        }

        existing.stream()
                .filter(s -> !incomingIds.contains(s.getId()))
                .forEach(s -> resumeSectionMapper.deleteById(s.getId()));
    }

    private String toJsonContent(String raw) {
        if (raw == null || raw.isBlank() || "{}".equals(raw)) {
            return raw;
        }
        try {
            objectMapper.readTree(raw);
            return raw;
        } catch (Exception e) {
            try {
                return objectMapper.writeValueAsString(raw);
            } catch (JsonProcessingException ex) {
                return raw;
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ResumeVO getById(Long id, Long userId) {
        ResumeEntity entity = getOwnedEntity(id, userId);
        return ResumeVO.fromEntity(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public ResumeVO getByIdWithSections(Long id, Long userId) {
        ResumeEntity entity = getOwnedEntity(id, userId);
        List<SectionVO> sections = resumeSectionMapper.findByResumeId(id).stream()
                .map(SectionVO::fromEntity)
                .toList();
        ResumeVO vo = ResumeVO.fromEntity(entity);
        return new ResumeVO(
                vo.id(), vo.userId(), vo.fileName(), vo.fileUrl(),
                vo.contentText(), vo.contentJson(), vo.isActive(),
                vo.title(), vo.template(), vo.language(), vo.themeConfig(),
                vo.isPublic(), vo.shareToken(), vo.viewCount(),
                sections, vo.createTime(), vo.updateTime()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<ResumeVO> listByUserPage(Long userId, String keyword, int page, int pageSize) {
        var wrapper = new LambdaQueryWrapper<ResumeEntity>()
                .eq(ResumeEntity::getUserId, userId);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w
                    .like(ResumeEntity::getTitle, keyword)
                    .or()
                    .like(ResumeEntity::getFileName, keyword));
        }
        wrapper.orderByDesc(ResumeEntity::getUpdateTime);
        var pageResult = resumeMapper.selectPage(new Page<>(page, pageSize), wrapper);
        var list = pageResult.getRecords().stream().map(ResumeVO::fromEntity).toList();
        return PageResult.success(list, page, pageSize, pageResult.getTotal());
    }

    @Override
    public void delete(Long id, Long userId) {
        ResumeEntity entity = getOwnedEntity(id, userId);
        resumeSectionMapper.deleteByResumeId(id);
        resumeMapper.deleteById(entity.getId());
        log.info("Resume deleted: id={}, userId={}", id, userId);
    }

    @Override
    public ResumeVO setActive(Long id, Long userId) {
        ResumeEntity entity = getOwnedEntity(id, userId);

        // 将该用户所有简历置为非激活
        ResumeEntity deactivateAll = new ResumeEntity();
        deactivateAll.setIsActive(false);
        LambdaUpdateWrapper<ResumeEntity> updateWrapper = new LambdaUpdateWrapper<ResumeEntity>()
                .eq(ResumeEntity::getUserId, userId);
        resumeMapper.update(deactivateAll, updateWrapper);

        // 激活目标简历
        entity.setIsActive(true);
        resumeMapper.updateById(entity);

        log.info("Resume set active: id={}, userId={}", id, userId);
        return ResumeVO.fromEntity(entity);
    }

    @Override
    public ResumeVO duplicate(Long id, Long userId) {
        ResumeEntity entity = getOwnedEntity(id, userId);

        ResumeEntity copy = new ResumeEntity();
        copy.setUserId(userId);
        copy.setTitle(entity.getTitle() != null ? entity.getTitle() + "(副本)" : null);
        copy.setFileName(entity.getFileName());
        copy.setFileUrl(null);
        copy.setContentText(entity.getContentText());
        copy.setContentJson(entity.getContentJson());
        copy.setTemplate(entity.getTemplate());
        copy.setLanguage(entity.getLanguage());
        copy.setIsActive(false);

        resumeMapper.insert(copy);

        List<ResumeSectionEntity> sections = resumeSectionMapper.findByResumeId(id);
        for (ResumeSectionEntity s : sections) {
            ResumeSectionEntity sectionCopy = new ResumeSectionEntity();
            sectionCopy.setResumeId(copy.getId());
            sectionCopy.setType(s.getType());
            sectionCopy.setTitle(s.getTitle());
            sectionCopy.setSortOrder(s.getSortOrder());
            sectionCopy.setVisible(s.getVisible());
            sectionCopy.setContent(s.getContent());
            resumeSectionMapper.insert(sectionCopy);
        }

        log.info("Resume duplicated: fromId={}, newId={}, userId={}", id, copy.getId(), userId);
        return getByIdWithSections(copy.getId(), userId);
    }

    @Override
    @Transactional(readOnly = true)
    public String export(Long id, Long userId, String format) {
        ResumeEntity entity = getOwnedEntity(id, userId);
        List<ResumeSectionEntity> sections = resumeSectionMapper.findByResumeId(id);

        return switch (format) {
            case "json" -> exportJson(entity, sections);
            case "txt" -> exportTxt(entity, sections);
            default -> exportHtml(entity, sections);
        };
    }

    private String exportJson(ResumeEntity entity, List<ResumeSectionEntity> sections) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(
                    ResumeVO.fromEntity(entity));
        } catch (JsonProcessingException e) {
            throw new BadRequestException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private String exportTxt(ResumeEntity entity, List<ResumeSectionEntity> sections) {
        StringBuilder sb = new StringBuilder();
        String title = entity.getTitle() != null ? entity.getTitle() : "Resume";
        sb.append(title).append("\n").append("=".repeat(title.length())).append("\n\n");
        for (ResumeSectionEntity s : sections) {
            sb.append(s.getTitle() != null ? s.getTitle() : s.getType()).append("\n");
            sb.append("-".repeat(s.getTitle() != null ? s.getTitle().length() : s.getType().length())).append("\n");
            if (s.getContent() != null && !s.getContent().equals("{}")) {
                try {
                    var content = objectMapper.readTree(s.getContent());
                    sb.append(content.toString()).append("\n");
                } catch (Exception e) {
                    sb.append(s.getContent()).append("\n");
                }
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private String exportHtml(ResumeEntity entity, List<ResumeSectionEntity> sections) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html lang=\"").append(entity.getLanguage() != null ? entity.getLanguage() : "zh")
                .append("\"><head><meta charset=\"UTF-8\"><title>");
        sb.append(entity.getTitle() != null ? entity.getTitle() : "Resume");
        sb.append("</title><style>body{font-family:sans-serif;max-width:800px;margin:0 auto;padding:20px}")
                .append("h1{text-align:center}section{margin:20px 0}h2{border-bottom:1px solid #ccc}</style>")
                .append("</head><body>");
        sb.append("<h1>").append(entity.getTitle() != null ? entity.getTitle() : "Resume").append("</h1>");
        for (ResumeSectionEntity s : sections) {
            if (s.getVisible() != null && !s.getVisible()) continue;
            sb.append("<section><h2>").append(s.getTitle() != null ? s.getTitle() : s.getType()).append("</h2>");
            if (s.getContent() != null && !s.getContent().equals("{}")) {
                sb.append("<div>").append(s.getContent()).append("</div>");
            }
            sb.append("</section>");
        }
        sb.append("</body></html>");
        return sb.toString();
    }

    @Override
    public ResumeVO uploadFile(Long userId, MultipartFile file) {
        // 校验文件是否为空
        if (file == null || file.isEmpty()) {
            throw new BadRequestException(ErrorCode.RESUME_PARSE_FAILED);
        }

        // 校验文件类型：仅允许 PDF
        String contentType = file.getContentType();
        String originalFilename = file.getOriginalFilename();
        if (contentType == null || !contentType.equalsIgnoreCase(PDF_CONTENT_TYPE)) {
            // 同时也检查扩展名兜底
            if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".pdf")) {
                throw new BadRequestException(ErrorCode.RESUME_PARSE_FAILED);
            }
        }

        // 校验文件大小（最大 10MB）
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BadRequestException(ErrorCode.RESUME_TOO_LARGE);
        }

        try {
            // 按日期分目录存储：uploads/resume/yyyy/MM/dd/uuid_filename.pdf
            String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
            String safeFileName = UUID.randomUUID() + "_" + (originalFilename != null ? originalFilename : "resume.pdf");
            String relativePath = dateStr + "/" + safeFileName;
            Path targetPath = Paths.get(uploadDir, relativePath);
            Files.createDirectories(targetPath.getParent());
            file.transferTo(targetPath.toFile());

            ResumeEntity entity = new ResumeEntity();
            entity.setUserId(userId);
            entity.setFileName(originalFilename);
            entity.setFileUrl(relativePath);
            entity.setContentText("[PDF text extraction pending]");
            entity.setContentJson("{}");
            entity.setTemplate("classic");
            entity.setLanguage("zh");

            // 如果是该用户的第一份简历，自动设为激活
            boolean isFirst = resumeMapper.selectCount(
                    new LambdaQueryWrapper<ResumeEntity>()
                            .eq(ResumeEntity::getUserId, userId)
            ) == 0;
            entity.setIsActive(isFirst);

            resumeMapper.insert(entity);
            log.info("Resume uploaded: userId={}, resumeId={}, fileName={}",
                    userId, entity.getId(), originalFilename);
            return ResumeVO.fromEntity(entity);

        } catch (IOException e) {
            log.error("Failed to save uploaded resume file", e);
            throw new BadRequestException(ErrorCode.RESUME_PARSE_FAILED);
        }
    }

    @Override
    public AiChatResponse aiChat(Long userId, AiChatRequest request) {
        String sessionId = request.sessionId();
        String sessionKey = "resume:ai:session:" + userId + ":" + sessionId;
        String completedKey = "resume:ai:completed:" + userId + ":" + sessionId;
        String roundsKey = "resume:ai:rounds:" + userId + ":" + sessionId;
        String lockKey = "resume:ai:lock:" + userId;

        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", 30, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(locked)) {
            return AiChatResponse.error("会话正在处理中，请稍后重试");
        }

        try {
            String historyJson = stringRedisTemplate.opsForValue().get(sessionKey);
            if (historyJson == null) {
                Result<QuotaCheckResult> consumeResult = quotaClient.consume("RESUME_CHAT");
                QuotaCheckResult checkResult = consumeResult.data();
                if (checkResult == null || !checkResult.allowed()) {
                    throw new BusinessException(
                            ErrorCode.QUOTA_EXHAUSTED.getCode(),
                            checkResult != null ? checkResult.reason() : "QUOTA_EXHAUSTED");
                }
            }
            List<Map<String, String>> history = historyJson != null
                    ? objectMapper.readValue(historyJson, new TypeReference<>() {})
                    : new ArrayList<>();

            String roundsJson = stringRedisTemplate.opsForValue().get(roundsKey);
            int roundCount = roundsJson != null ? Integer.parseInt(roundsJson) : 0;

            history.add(Map.of("role", "user", "content", request.message()));

            StringBuilder msgBuilder = new StringBuilder("以下是当前对话历史：\n");
            for (Map<String, String> msg : history) {
                msgBuilder.append(msg.get("role")).append(": ").append(msg.get("content")).append("\n");
            }
            msgBuilder.append("\n请根据以上对话历史，给出你的回复（JSON 格式）。");

            String aiResponse = aiClient.chat(AI_CHAT_SYSTEM_PROMPT, msgBuilder.toString(), "json_object");
            JsonNode root = objectMapper.readTree(aiResponse);

            history.add(Map.of("role", "assistant", "content", aiResponse));

            if ("complete".equals(root.path("type").asText())) {
                String title = root.path("title").asText();
                JsonNode sectionsNode = root.path("sections");
                List<SectionRequest> sections = new ArrayList<>();
                if (sectionsNode.isArray()) {
                    for (JsonNode s : sectionsNode) {
                        String rawContent = s.path("content").asText();
                        String jsonContent = toJsonContent(rawContent);
                        sections.add(new SectionRequest(
                                null, s.path("type").asText(), s.path("title").asText(),
                                s.path("sortOrder").asInt(0), true, jsonContent));
                    }
                }
                sections = ensureAllSections(sections);
                StringBuilder contentText = new StringBuilder();
                for (SectionRequest s : sections) {
                    contentText.append(s.title()).append(": ").append(s.content()).append("\n");
                }
                ResumeCreateRequest result = new ResumeCreateRequest(
                        null, contentText.toString(), null, title, sections, request.sessionId());

                stringRedisTemplate.opsForValue().set(completedKey,
                        objectMapper.writeValueAsString(result), 24, TimeUnit.HOURS);
                stringRedisTemplate.delete(sessionKey);
                stringRedisTemplate.delete(roundsKey);

                return AiChatResponse.complete(result);
            } else {
                roundCount++;
                stringRedisTemplate.opsForValue().set(roundsKey, String.valueOf(roundCount), 30, TimeUnit.MINUTES);
                stringRedisTemplate.opsForValue().set(sessionKey,
                        objectMapper.writeValueAsString(history), 30, TimeUnit.MINUTES);

                if (roundCount >= MAX_FOLLOW_UP_ROUNDS) {
                    return forceComplete(request, history, completedKey, sessionKey, roundsKey);
                }

                String content = root.path("content").asText();
                JsonNode optionsNode = root.path("options");
                List<String> options = optionsNode.isArray()
                        ? objectMapper.convertValue(optionsNode, new TypeReference<>() {})
                        : List.of();

                return options.isEmpty()
                        ? AiChatResponse.text(content)
                        : AiChatResponse.options(content, options);
            }
        } catch (Exception e) {
            log.error("AI chat failed: sessionId={}", request.sessionId(), e);
            return AiChatResponse.error("AI 服务异常，请稍后重试");
        } finally {
            stringRedisTemplate.delete(lockKey);
        }
    }

    @Override
    public ResumeCreateRequest getAiChatResult(Long userId, String sessionId) {
        String json = stringRedisTemplate.opsForValue().get("resume:ai:completed:" + userId + ":" + sessionId);
        if (json == null) {
            throw new NotFoundException(ErrorCode.RESUME_NOT_FOUND);
        }
        try {
            return objectMapper.readValue(json, ResumeCreateRequest.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse completed AI chat result: sessionId={}", sessionId, e);
            throw new BadRequestException(ErrorCode.INTERNAL_ERROR);
        }
    }

    @Override
    public PolishResponse polishSection(Long userId, PolishRequest request) {
        if (request.resumeId() != null) {
            return polishBatch(userId, request);
        }
        return polishSingle(userId, request);
    }

    private PolishResponse polishSingle(Long userId, PolishRequest request) {
        String prompt = SECTION_PROMPTS.get(request.sectionType());
        if (prompt == null) {
            return new PolishResponse(request.rawText());
        }
        if (request.rawText() == null || request.rawText().isBlank()) {
            return new PolishResponse("");
        }
        try {
            String example = SECTION_EXAMPLES.get(request.sectionType());
            String result;
            if ("json".equals(request.format())) {
                JsonNode rawArray = objectMapper.readTree(request.rawText());
                if (!rawArray.isArray()) {
                    throw new BadRequestException(ErrorCode.BAD_REQUEST);
                }
                String userMsg = "<example>\n" + example + "\n</example>\n\n请将以下 JSON 数组中的每一项润色，以 JSON 数组格式返回润色后的每一项：\n" + request.rawText();
                String aiResponse = aiClient.chat(prompt, userMsg, "json_object");
                JsonNode resultArray = objectMapper.readTree(aiResponse);
                if (!resultArray.isArray()) {
                    throw new BadRequestException(ErrorCode.LLM_SERVICE_ERROR);
                }
                result = objectMapper.writeValueAsString(resultArray);
            } else {
                String userMsg = "<example>\n" + example + "\n</example>\n\n请将以下内容润色：\n" + request.rawText();
                result = aiClient.chat(prompt, userMsg);
            }
            return new PolishResponse(result);
        } catch (JsonProcessingException e) {
            log.error("JSON processing failed: sectionType={}, userId={}", request.sectionType(), userId, e);
            throw new BadRequestException(ErrorCode.BAD_REQUEST);
        } catch (Exception e) {
            log.error("AI polish failed: sectionType={}, userId={}", request.sectionType(), userId, e);
            throw new BadRequestException(ErrorCode.LLM_SERVICE_ERROR);
        }
    }

    private PolishResponse polishBatch(Long userId, PolishRequest request) {
        ResumeEntity entity = getOwnedEntity(request.resumeId(), userId);
        List<ResumeSectionEntity> sections = resumeSectionMapper.findByResumeId(entity.getId());
        if (request.sectionTypes() != null && !request.sectionTypes().isEmpty()) {
            sections = sections.stream()
                    .filter(s -> request.sectionTypes().contains(s.getType()))
                    .toList();
        }
        if (sections.isEmpty()) {
            return new PolishResponse(List.of());
        }
        List<PolishResponse.SectionResult> results = new ArrayList<>();
        for (ResumeSectionEntity section : sections) {
            String prompt = SECTION_PROMPTS.get(section.getType());
            if (prompt == null || section.getContent() == null || section.getContent().isBlank()) {
                results.add(new PolishResponse.SectionResult(section.getType(), section.getContent()));
                continue;
            }
            try {
                JsonNode contentNode = objectMapper.readTree(section.getContent());
                if (!contentNode.isObject()) {
                    results.add(new PolishResponse.SectionResult(section.getType(), section.getContent()));
                    continue;
                }
                ObjectNode polishedNode = contentNode.deepCopy();
                java.util.Iterator<Map.Entry<String, JsonNode>> fields = contentNode.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> field = fields.next();
                    JsonNode value = field.getValue();
                    String example = SECTION_EXAMPLES.get(section.getType());
                    if (value.isArray() && value.size() > 0) {
                        String rawArray = objectMapper.writeValueAsString(value);
                        String userMsg = "<example>\n" + example + "\n</example>\n\n请将以下 JSON 数组中描述润色，以 JSON 数组格式返回润色后的每一项：\n" + rawArray;
                        String aiResponse = aiClient.chat(prompt, userMsg, "json_object");
                        polishedNode.set(field.getKey(), objectMapper.readTree(aiResponse));
                    } else if (value.isTextual() && !value.asText().isBlank()) {
                        String userMsg = "<example>\n" + example + "\n</example>\n\n请将以下内容润色：\n" + value.asText();
                        String polished = aiClient.chat(prompt, userMsg);
                        polishedNode.put(field.getKey(), polished);
                    }
                }
                String polishedContent = objectMapper.writeValueAsString(polishedNode);
                section.setContent(polishedContent);
                resumeSectionMapper.updateById(section);
                results.add(new PolishResponse.SectionResult(section.getType(), polishedContent));
            } catch (Exception e) {
                log.error("AI polish failed: sectionId={}, type={}, userId={}", section.getId(), section.getType(), userId, e);
                throw new BadRequestException(ErrorCode.LLM_SERVICE_ERROR);
            }
        }
        return new PolishResponse(results);
    }

    private List<SectionRequest> ensureAllSections(List<SectionRequest> sections) {
        if (sections == null) {
            sections = new ArrayList<>();
        }
        Set<String> existingTypes = sections.stream()
                .map(SectionRequest::type)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        List<SectionRequest> result = new ArrayList<>(sections);
        for (int i = 0; i < REQUIRED_SECTION_TYPES.size(); i++) {
            String type = REQUIRED_SECTION_TYPES.get(i);
            if (!existingTypes.contains(type)) {
                result.add(new SectionRequest(
                        null, type, SECTION_DEFAULT_TITLES.get(type),
                        i, true, DEFAULT_SECTION_CONTENT.get(type)));
            }
        }
        result.sort(java.util.Comparator.comparingInt(SectionRequest::sortOrder));
        return result;
    }

    private AiChatResponse forceComplete(AiChatRequest request,
                                      List<Map<String, String>> history,
                                      String completedKey, String sessionKey, String roundsKey) {
        try {
            StringBuilder msgBuilder = new StringBuilder("以下是当前对话历史：\n");
            for (Map<String, String> msg : history) {
                msgBuilder.append(msg.get("role")).append(": ").append(msg.get("content")).append("\n");
            }
            msgBuilder.append("\n以上信息已足够，请立即输出 complete 格式的简历（必须包含全部 6 个基础 section type）。");

            String aiResponse = aiClient.chat(AI_CHAT_SYSTEM_PROMPT, msgBuilder.toString(), "json_object");
            JsonNode root = objectMapper.readTree(aiResponse);

            String title = root.path("title").asText("生成的简历");
            JsonNode sectionsNode = root.path("sections");
            List<SectionRequest> sections = new ArrayList<>();
            if (sectionsNode.isArray()) {
                for (JsonNode s : sectionsNode) {
                    String rawContent = s.path("content").asText();
                    String jsonContent = toJsonContent(rawContent);
                    sections.add(new SectionRequest(
                            null, s.path("type").asText(), s.path("title").asText(),
                            s.path("sortOrder").asInt(0), true, jsonContent));
                }
            }
            sections = ensureAllSections(sections);
            StringBuilder contentText = new StringBuilder();
            for (SectionRequest s : sections) {
                contentText.append(s.title()).append(": ").append(s.content()).append("\n");
            }
            ResumeCreateRequest result = new ResumeCreateRequest(
                    null, contentText.toString(), null, title, sections, request.sessionId());

            stringRedisTemplate.opsForValue().set(completedKey,
                    objectMapper.writeValueAsString(result), 24, TimeUnit.HOURS);
            stringRedisTemplate.delete(sessionKey);
            stringRedisTemplate.delete(roundsKey);

            return AiChatResponse.complete(result);
        } catch (Exception e) {
            log.error("Force complete failed: sessionId={}", request.sessionId(), e);
            return AiChatResponse.error("AI 服务异常，请稍后重试");
        }
    }

    /**
     * 获取简历并校验所有权
     *
     * @param id     简历 ID
     * @param userId 用户 ID
     * @return 简历实体
     * @throws NotFoundException 简历不存在或不属于该用户
     */
    private ResumeEntity getOwnedEntity(Long id, Long userId) {
        ResumeEntity entity = resumeMapper.selectById(id);
        if (entity == null) {
            throw new NotFoundException(ErrorCode.RESUME_NOT_FOUND);
        }
        if (!userId.equals(entity.getUserId())) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN);
        }
        return entity;
    }
}
