package com.prepdeck.job.service;

import com.prepdeck.job.dto.request.AiCoverLetterRequest;
import com.prepdeck.job.dto.request.JobApplicationCreateRequest;
import com.prepdeck.job.dto.request.JobApplicationUpdateRequest;
import com.prepdeck.job.dto.response.AiCoverLetterVO;
import com.prepdeck.job.dto.response.JobApplicationPageResponse;
import com.prepdeck.job.dto.response.JobApplicationVO;

import java.util.List;

public interface JobApplicationService {

    JobApplicationVO create(Long userId, JobApplicationCreateRequest request);

    JobApplicationVO update(Long userId, String id, JobApplicationUpdateRequest request);

    JobApplicationVO getById(Long userId, String id);

    void delete(Long userId, String id);

    JobApplicationPageResponse list(Long userId, String status, String keyword, int page, int pageSize);
}
