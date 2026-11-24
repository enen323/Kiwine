package com.prepdeck.job.controller;

import com.prepdeck.common.model.PageResult;
import com.prepdeck.common.model.Result;
import com.prepdeck.common.util.SecurityContext;
import com.prepdeck.job.dto.request.AiCoverLetterRequest;
import com.prepdeck.job.dto.response.AiCoverLetterVO;
import com.prepdeck.job.service.AiCoverLetterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ai/resumes/{resumeId}/cover-letters")
@RequiredArgsConstructor
public class AiCoverLetterController {

    private final AiCoverLetterService aiCoverLetterService;

    @GetMapping
    public Result<List<AiCoverLetterVO>> list(@PathVariable String resumeId) {
        Long userId = SecurityContext.getCurrentUserId();
        return Result.success(aiCoverLetterService.listByResumeId(userId, resumeId));
    }

    @GetMapping("/{coverLetterId}")
    public Result<AiCoverLetterVO> getById(@PathVariable String resumeId,
                                           @PathVariable String coverLetterId) {
        Long userId = SecurityContext.getCurrentUserId();
        return Result.success(aiCoverLetterService.getById(userId, coverLetterId));
    }

    @PutMapping("/{coverLetterId}")
    public Result<AiCoverLetterVO> update(@PathVariable String resumeId,
                                          @PathVariable String coverLetterId,
                                          @Valid @RequestBody AiCoverLetterRequest request) {
        Long userId = SecurityContext.getCurrentUserId();
        return Result.success(aiCoverLetterService.update(userId, coverLetterId, request));
    }

    @DeleteMapping("/{coverLetterId}")
    public Result<Void> delete(@PathVariable String resumeId,
                               @PathVariable String coverLetterId) {
        Long userId = SecurityContext.getCurrentUserId();
        aiCoverLetterService.delete(userId, coverLetterId);
        return Result.success();
    }
}
