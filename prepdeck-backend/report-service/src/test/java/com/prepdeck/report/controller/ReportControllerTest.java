package com.prepdeck.report.controller;

import com.prepdeck.common.model.Result;
import com.prepdeck.report.dto.request.GenerateReportRequest;
import com.prepdeck.report.dto.response.*;
import com.prepdeck.report.service.ReportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportControllerTest {

    @Mock
    private ReportService reportService;

    @Test
    void generateReport() {
        when(reportService.generateReport(any(GenerateReportRequest.class)))
                .thenReturn(new InterviewReport(
                        new OverallSummary(2, 75.5, 90, 60, 3600),
                        new DimensionScores(List.of()),
                        new StrongAreas(List.of()),
                        new WeakAreas(List.of()),
                        new TrendData(List.of()),
                        new ImprovementSuggestions(List.of())
                ));

        ReportController controller = new ReportController(reportService);
        GenerateReportRequest request = new GenerateReportRequest(null, null, List.of());
        Result<InterviewReport> result = controller.generate(request);

        assertEquals(0, result.code());
        assertNotNull(result.data());
        verify(reportService).generateReport(request);
    }

    @Test
    void getAvailableInterviews() {
        when(reportService.getAvailableInterviews())
                .thenReturn(List.of(new InterviewOption(1L, "ALIBABA", "BACKEND", LocalDateTime.now(), 80)));

        ReportController controller = new ReportController(reportService);
        Result<List<InterviewOption>> result = controller.getAvailableInterviews();

        assertEquals(0, result.code());
        assertEquals(1, result.data().size());
        verify(reportService).getAvailableInterviews();
    }

    @Test
    void generate_withNullFields_stillWorks() {
        when(reportService.generateReport(any(GenerateReportRequest.class)))
                .thenReturn(new InterviewReport(
                        new OverallSummary(0, 0, 0, 0, 0),
                        new DimensionScores(List.of()),
                        new StrongAreas(List.of()),
                        new WeakAreas(List.of()),
                        new TrendData(List.of()),
                        new ImprovementSuggestions(List.of())
                ));

        ReportController controller = new ReportController(reportService);
        GenerateReportRequest request = new GenerateReportRequest(null, null, null);
        Result<InterviewReport> result = controller.generate(request);

        assertEquals(0, result.code());
        assertNotNull(result.data());
        verify(reportService).generateReport(request);
    }

    @Test
    void generate_withNonExistentInterviewIds_returnsEmptyReport() {
        when(reportService.generateReport(any(GenerateReportRequest.class)))
                .thenReturn(new InterviewReport(
                        new OverallSummary(0, 0, 0, 0, 0),
                        new DimensionScores(List.of()),
                        new StrongAreas(List.of()),
                        new WeakAreas(List.of()),
                        new TrendData(List.of()),
                        new ImprovementSuggestions(List.of())
                ));

        ReportController controller = new ReportController(reportService);
        GenerateReportRequest request = new GenerateReportRequest(null, null, List.of(9999L));
        Result<InterviewReport> result = controller.generate(request);

        assertEquals(0, result.code());
        assertEquals(0, result.data().summary().totalInterviews());
        verify(reportService).generateReport(request);
    }

    @Test
    void getAvailableInterviews_returnsListWrappedInResult() {
        List<InterviewOption> options = List.of(
                new InterviewOption(1L, "ALIBABA", "BACKEND", LocalDateTime.now(), 80),
                new InterviewOption(2L, "BYTEDANCE", "FRONTEND", LocalDateTime.now(), 90)
        );
        when(reportService.getAvailableInterviews()).thenReturn(options);

        ReportController controller = new ReportController(reportService);
        Result<List<InterviewOption>> result = controller.getAvailableInterviews();

        assertEquals(0, result.code());
        assertEquals(2, result.data().size());
        verify(reportService).getAvailableInterviews();
    }
}
