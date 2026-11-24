package com.prepdeck.resume.service;

import com.prepdeck.common.model.PageResult;
import com.prepdeck.resume.dto.request.AiChatRequest;
import com.prepdeck.resume.dto.request.PolishRequest;
import com.prepdeck.resume.dto.request.ResumeCreateRequest;
import com.prepdeck.resume.dto.request.ResumeUpdateRequest;
import com.prepdeck.resume.dto.response.AiChatResponse;
import com.prepdeck.resume.dto.response.PolishResponse;
import com.prepdeck.resume.dto.response.ResumeVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ResumeService {

    ResumeVO create(Long userId, ResumeCreateRequest request);

    ResumeVO update(Long id, Long userId, ResumeUpdateRequest request);

    ResumeVO getById(Long id, Long userId);

    ResumeVO getByIdWithSections(Long id, Long userId);

    PageResult<ResumeVO> listByUserPage(Long userId, String keyword, int page, int pageSize);

    void delete(Long id, Long userId);

    ResumeVO setActive(Long id, Long userId);

    ResumeVO duplicate(Long id, Long userId);

    ResumeVO uploadFile(Long userId, MultipartFile file);

    String export(Long id, Long userId, String format);

    PolishResponse polishSection(Long userId, PolishRequest request);

    AiChatResponse aiChat(Long userId, AiChatRequest request);

    ResumeCreateRequest getAiChatResult(Long userId, String sessionId);
}
