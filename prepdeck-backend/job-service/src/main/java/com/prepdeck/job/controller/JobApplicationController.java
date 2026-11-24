package com.prepdeck.job.controller;

import com.prepdeck.common.model.Result;
import com.prepdeck.common.util.SecurityContext;
import com.prepdeck.job.dto.request.AiCoverLetterRequest;
import com.prepdeck.job.dto.request.JobApplicationCreateRequest;
import com.prepdeck.job.dto.request.JobApplicationUpdateRequest;
import com.prepdeck.job.dto.response.AiCoverLetterVO;
import com.prepdeck.job.dto.response.JobApplicationPageResponse;
import com.prepdeck.job.dto.response.JobApplicationVO;
import com.prepdeck.job.service.AiCoverLetterService;
import com.prepdeck.job.service.JobApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/applications")
@RequiredArgsConstructor
public class JobApplicationController {

    private final JobApplicationService jobApplicationService;
    private final AiCoverLetterService aiCoverLetterService;

    @GetMapping("/health")
    public Result<String> health() {
        return Result.success("job-service is running");
    }

    @PostMapping
    public Result<JobApplicationVO> create(@Valid @RequestBody JobApplicationCreateRequest request) {
        Long userId = SecurityContext.getCurrentUserId();
        return Result.success(jobApplicationService.create(userId, request));
    }

    @PutMapping("/{id}")
    public Result<JobApplicationVO> update(@PathVariable String id,
                                           @Valid @RequestBody JobApplicationUpdateRequest request) {
        Long userId = SecurityContext.getCurrentUserId();
        return Result.success(jobApplicationService.update(userId, id, request));
    }

    @GetMapping("/{id}")
    public Result<JobApplicationVO> getById(@PathVariable String id) {
        Long userId = SecurityContext.getCurrentUserId();
        return Result.success(jobApplicationService.getById(userId, id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        Long userId = SecurityContext.getCurrentUserId();
        jobApplicationService.delete(userId, id);
        return Result.success();
    }

    @GetMapping
    public Result<JobApplicationPageResponse> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        Long userId = SecurityContext.getCurrentUserId();
        return Result.success(jobApplicationService.list(userId, status, keyword, page, pageSize));
    }

    @PostMapping("/{id}/cover-letters")
    public Result<AiCoverLetterVO> generateCoverLetter(@PathVariable String id,
                                                       @Valid @RequestBody AiCoverLetterRequest request) {
        Long userId = SecurityContext.getCurrentUserId();
        return Result.success(aiCoverLetterService.generate(userId, id, request));
    }
}
