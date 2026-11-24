package com.prepdeck.report.service;

import com.prepdeck.common.util.SecurityContext;
import com.prepdeck.report.dto.request.GenerateReportRequest;
import com.prepdeck.report.dto.response.InterviewOption;
import com.prepdeck.report.dto.response.InterviewReport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
public class ReportServiceImpl implements ReportService {

    private final ReportGenerator reportGenerator;

    @Override
    public InterviewReport generateReport(GenerateReportRequest request) {
        Long userId = SecurityContext.getCurrentUserId();
        return reportGenerator.generate(userId, request.interviewIds());
    }

    @Override
    public List<InterviewOption> getAvailableInterviews() {
        Long userId = SecurityContext.getCurrentUserId();
        return reportGenerator.getAvailableInterviews(userId);
    }
}