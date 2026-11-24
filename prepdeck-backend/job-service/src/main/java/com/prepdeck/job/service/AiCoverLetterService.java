package com.prepdeck.job.service;

import com.prepdeck.job.dto.request.AiCoverLetterRequest;
import com.prepdeck.job.dto.response.AiCoverLetterVO;

import java.util.List;

public interface AiCoverLetterService {

    AiCoverLetterVO generate(Long userId, String applicationId, AiCoverLetterRequest request);

    List<AiCoverLetterVO> listByResumeId(Long userId, String resumeId);

    AiCoverLetterVO getById(Long userId, String coverLetterId);

    AiCoverLetterVO update(Long userId, String coverLetterId, AiCoverLetterRequest request);

    void delete(Long userId, String coverLetterId);
}
