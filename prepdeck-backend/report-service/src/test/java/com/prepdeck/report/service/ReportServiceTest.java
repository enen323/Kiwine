package com.prepdeck.report.service;

import com.prepdeck.report.dto.request.GenerateReportRequest;
import com.prepdeck.report.dto.response.*;
import com.prepdeck.report.repository.ReportInterviewRecordMapper;
import com.prepdeck.report.repository.ReportQuestionScoreMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private ReportGenerator reportGenerator;

    private ReportService reportService;

    @BeforeEach
    void setUp() {
        reportService = new ReportServiceImpl(reportGenerator);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void generateReport_passesCurrentUserIdToGenerator() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Gateway-Source", "prepdeck-gateway");
        request.addHeader("X-Gateway-Secret", "test-secret");
        request.addHeader("X-User-Id", "42");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        GenerateReportRequest req = new GenerateReportRequest(null, null, List.of(1L, 2L));
        InterviewReport mockReport = new InterviewReport(
                new OverallSummary(2, 75.0, 90, 60, 3600),
                new DimensionScores(List.of()),
                new StrongAreas(List.of()),
                new WeakAreas(List.of()),
                new TrendData(List.of()),
                new ImprovementSuggestions(List.of())
        );
        when(reportGenerator.generate(42L, req.interviewIds())).thenReturn(mockReport);

        InterviewReport result = reportService.generateReport(req);

        assertSame(mockReport, result);
        verify(reportGenerator).generate(42L, req.interviewIds());
    }

    @Test
    void generateReport_nullInterviewIds_delegatesToGenerator() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Gateway-Source", "prepdeck-gateway");
        request.addHeader("X-Gateway-Secret", "test-secret");
        request.addHeader("X-User-Id", "1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        GenerateReportRequest req = new GenerateReportRequest(null, null, null);
        InterviewReport mockReport = new InterviewReport(
                new OverallSummary(0, 0, 0, 0, 0),
                new DimensionScores(List.of()),
                new StrongAreas(List.of()),
                new WeakAreas(List.of()),
                new TrendData(List.of()),
                new ImprovementSuggestions(List.of())
        );
        when(reportGenerator.generate(1L, null)).thenReturn(mockReport);

        InterviewReport result = reportService.generateReport(req);

        assertSame(mockReport, result);
        verify(reportGenerator).generate(1L, null);
    }

    @Test
    void getAvailableInterviews_delegatesToGenerator() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Gateway-Source", "prepdeck-gateway");
        request.addHeader("X-Gateway-Secret", "test-secret");
        request.addHeader("X-User-Id", "7");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        List<InterviewOption> options = List.of(
                new InterviewOption(1L, "ALIBABA", "BACKEND", null, 80)
        );
        when(reportGenerator.getAvailableInterviews(7L)).thenReturn(options);

        List<InterviewOption> result = reportService.getAvailableInterviews();

        assertEquals(options, result);
        verify(reportGenerator).getAvailableInterviews(7L);
    }
}
