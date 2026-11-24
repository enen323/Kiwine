package com.prepdeck.report.service;

import com.prepdeck.report.dto.response.InterviewOption;
import com.prepdeck.report.dto.response.InterviewReport;

import java.util.List;

public interface ReportGenerator {

    InterviewReport generate(Long userId, List<Long> interviewIds);

    List<InterviewOption> getAvailableInterviews(Long userId);
}