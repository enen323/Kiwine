package com.prepdeck.report.service;

import com.prepdeck.report.dto.request.GenerateReportRequest;
import com.prepdeck.report.dto.response.InterviewOption;
import com.prepdeck.report.dto.response.InterviewReport;

import java.util.List;

public interface ReportService {

    InterviewReport generateReport(GenerateReportRequest request);

    List<InterviewOption> getAvailableInterviews();
}