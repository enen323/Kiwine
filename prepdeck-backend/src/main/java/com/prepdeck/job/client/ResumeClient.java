package com.prepdeck.job.client;

import com.prepdeck.common.model.Result;
import com.prepdeck.common.util.SecurityContext;
import com.prepdeck.job.dto.response.ResumeVO;
import com.prepdeck.resume.service.ResumeService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * 原 resume-service Feign 客户端，单体化后改为本地直调。
 *
 * <p>契约对照：ResumeController#getById（GET /api/v1/resumes/{id}）→ resumeService.getByIdWithSections(id, userId)。
 * 返回类型沿用 job 侧本地 DTO {@link ResumeVO}（原 Feign 接口即如此，调用方按 id/userId/title/contentText 四个
 * 字段消费），故由 resume 侧 ResumeVO 逐字段转换，不改变任何调用方的类型。</p>
 */
@Component("jobResumeClient")
public class ResumeClient {

    @Resource
    private ResumeService resumeService;

    public Result<ResumeVO> getResume(Long id) {
        com.prepdeck.resume.dto.response.ResumeVO resume =
                resumeService.getByIdWithSections(id, SecurityContext.getCurrentUserId());
        return Result.success(new ResumeVO(resume.id(), resume.userId(), resume.title(), resume.contentText()));
    }
}
