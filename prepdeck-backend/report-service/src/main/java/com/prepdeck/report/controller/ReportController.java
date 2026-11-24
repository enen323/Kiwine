package com.prepdeck.report.controller;

import com.prepdeck.common.model.Result;
import com.prepdeck.report.dto.request.GenerateReportRequest;
import com.prepdeck.report.dto.response.InterviewOption;
import com.prepdeck.report.dto.response.InterviewReport;
import com.prepdeck.report.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @PostMapping("/generate")
    public Result<InterviewReport> generate(@Valid @RequestBody GenerateReportRequest request) {
        return Result.success(reportService.generateReport(request));
    }

    @GetMapping("/interviews/available")
    public Result<List<InterviewOption>> getAvailableInterviews() {
        return Result.success(reportService.getAvailableInterviews());
    }
}