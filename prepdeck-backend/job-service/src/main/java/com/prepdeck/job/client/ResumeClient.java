package com.prepdeck.job.client;

import com.prepdeck.common.model.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "resume-service", path = "/api/v1/resumes", fallbackFactory = ResumeClientFallbackFactory.class)
public interface ResumeClient {

    @GetMapping("/{id}")
    Result<ResumeVO> getResume(@PathVariable("id") Long id);
}
