package com.prepdeck.resume.controller;

import com.prepdeck.common.model.PageResult;
import com.prepdeck.common.model.Result;
import com.prepdeck.common.util.SecurityContext;
import com.prepdeck.resume.dto.request.AiChatRequest;
import com.prepdeck.resume.dto.request.PolishRequest;
import com.prepdeck.resume.dto.request.ResumeCreateRequest;
import com.prepdeck.resume.dto.request.ResumeUpdateRequest;
import com.prepdeck.resume.dto.response.AiChatResponse;
import com.prepdeck.resume.dto.response.PolishResponse;
import com.prepdeck.resume.dto.response.ResumeVO;
import com.prepdeck.resume.service.ResumeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/v1/resumes")
@RequiredArgsConstructor
public class ResumeController {

    private final ResumeService resumeService;

    @GetMapping("/health")
    public Result<String> health() {
        return Result.success("resume-service is running");
    }

    @PostMapping
    public Result<ResumeVO> create(@Valid @RequestBody ResumeCreateRequest request) {
        Long userId = SecurityContext.getCurrentUserId();
        return Result.success(resumeService.create(userId, request));
    }

    @PutMapping("/{id}")
    public Result<ResumeVO> update(@PathVariable Long id,
                                   @Valid @RequestBody ResumeUpdateRequest request) {
        Long userId = SecurityContext.getCurrentUserId();
        return Result.success(resumeService.update(id, userId, request));
    }

    @GetMapping("/{id}")
    public Result<ResumeVO> getById(@PathVariable Long id) {
        Long userId = SecurityContext.getCurrentUserId();
        return Result.success(resumeService.getByIdWithSections(id, userId));
    }

    @GetMapping
    public Result<PageResult<ResumeVO>> listByUser(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        Long userId = SecurityContext.getCurrentUserId();
        return Result.success(resumeService.listByUserPage(userId, keyword, page, pageSize));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        Long userId = SecurityContext.getCurrentUserId();
        resumeService.delete(id, userId);
        return Result.success();
    }

    @PostMapping("/{id}/duplicate")
    public Result<ResumeVO> duplicate(@PathVariable Long id) {
        Long userId = SecurityContext.getCurrentUserId();
        return Result.success(resumeService.duplicate(id, userId));
    }

    @PatchMapping("/{id}/active")
    public Result<ResumeVO> setActive(@PathVariable Long id) {
        Long userId = SecurityContext.getCurrentUserId();
        return Result.success(resumeService.setActive(id, userId));
    }

    @GetMapping("/{id}/export")
    public Result<String> export(@PathVariable Long id,
                                 @RequestParam(defaultValue = "html") String format) {
        Long userId = SecurityContext.getCurrentUserId();
        String content = resumeService.export(id, userId, format);
        return Result.success(content);
    }

    @PostMapping("/upload")
    public Result<ResumeVO> uploadFile(@RequestParam("file") MultipartFile file) {
        Long userId = SecurityContext.getCurrentUserId();
        return Result.success(resumeService.uploadFile(userId, file));
    }

    @PostMapping("/polish")
    public Result<PolishResponse> polish(@Valid @RequestBody PolishRequest request) {
        Long userId = SecurityContext.getCurrentUserId();
        return Result.success(resumeService.polishSection(userId, request));
    }

    @PostMapping("/ai-chat")
    public Result<AiChatResponse> aiChat(@Valid @RequestBody AiChatRequest request) {
        Long userId = SecurityContext.getCurrentUserId();
        return Result.success(resumeService.aiChat(userId, request));
    }

    @GetMapping("/ai-chat/{sessionId}/result")
    public Result<ResumeCreateRequest> getAiChatResult(@PathVariable String sessionId) {
        Long userId = SecurityContext.getCurrentUserId();
        return Result.success(resumeService.getAiChatResult(userId, sessionId));
    }
}
