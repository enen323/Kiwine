package com.prepdeck.interview.client;

import com.prepdeck.resume.dto.response.ResumeVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "resume-service", path = "/api/v1/resumes", fallbackFactory = ResumeClientFallbackFactory.class)
public interface ResumeClient {

    @GetMapping("/{id}")
    ResumeVO getById(@PathVariable Long id);
}
