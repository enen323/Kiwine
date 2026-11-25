package com.prepdeck.interview.client;

import com.prepdeck.common.util.SecurityContext;
import com.prepdeck.resume.dto.response.ResumeVO;
import com.prepdeck.resume.service.ResumeService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * 原 resume-service Feign 客户端，单体化后改为本地直调。
 *
 * <p>契约对照：ResumeController#getById（GET /api/v1/resumes/{id}）→ resumeService.getByIdWithSections(id, userId)。
 * 该 controller 返回 Result&lt;ResumeVO&gt;，本客户端方法 historically 直接返回 ResumeVO（原接口即如此），
 * 故此处解包返回 data 部分。</p>
 */
@Component("interviewResumeClient")
public class ResumeClient {

    @Resource
    private ResumeService resumeService;

    public ResumeVO getById(Long id) {
        return resumeService.getByIdWithSections(id, SecurityContext.getCurrentUserId());
    }
}
