package com.prepdeck.report.service;

import com.prepdeck.report.domain.ReportInterviewRecord;
import com.prepdeck.report.domain.ReportQuestionScore;
import com.prepdeck.report.dto.response.*;
import com.prepdeck.report.repository.ReportInterviewRecordMapper;
import com.prepdeck.report.repository.ReportQuestionScoreMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportGeneratorTest {

    @Mock
    private ReportInterviewRecordMapper recordMapper;

    @Mock
    private ReportQuestionScoreMapper scoreMapper;

    private ReportGenerator generator;

    @BeforeEach
    void setUp() {
        Mockito.reset(recordMapper, scoreMapper);
        generator = new ReportGeneratorImpl(recordMapper, scoreMapper);
    }

    @Test
    void generate_emptyInterviews_returnsEmptyReport() {
        when(recordMapper.findCompletedByUserIdAndDateRange(anyLong(), any(), any())).thenReturn(List.of());

        InterviewReport report = generator.generate(1L, null);

        assertEquals(0, report.summary().totalInterviews());
        assertTrue(report.dimensionScores().dimensions().isEmpty());
    }

    @Test
    void generate_withInterviews_computesSummary() {
        ReportInterviewRecord interview = createInterview(1L, 80);
        when(recordMapper.findCompletedByUserIdAndIds(anyLong(), any())).thenReturn(List.of(interview));
        when(scoreMapper.findByInterviewIds(any())).thenReturn(List.of());

        InterviewReport report = generator.generate(1L, List.of(1L));

        assertEquals(1, report.summary().totalInterviews());
        assertEquals(80, report.summary().highestScore());
        assertEquals(80, report.summary().lowestScore());
        assertEquals(80, report.summary().avgScore(), 0.1);
    }

    @Test
    void generate_computesDimensionScores() {
        ReportInterviewRecord interview = createInterview(1L, 80);
        interview.setScoreTech(90);
        interview.setScoreExpression(70);
        interview.setScoreProject(85);
        interview.setScoreLogic(75);
        interview.setScoreAdaptability(80);
        when(recordMapper.findCompletedByUserIdAndIds(anyLong(), any())).thenReturn(List.of(interview));
        when(scoreMapper.findByInterviewIds(any())).thenReturn(List.of());

        InterviewReport report = generator.generate(1L, List.of(1L));

        List<DimensionScore> dimensions = report.dimensionScores().dimensions();
        assertEquals(5, dimensions.size());
        assertEquals("技术深度", dimensions.get(0).name());
        assertEquals(90.0, dimensions.get(0).score(), 0.1);
        assertEquals(1, dimensions.get(0).rank());
    }

    @Test
    void generate_singleInterview_trendHasOnePoint() {
        ReportInterviewRecord interview = createInterview(1L, 75);
        interview.setCompletedAt(LocalDateTime.of(2024, 1, 1, 10, 0));
        when(recordMapper.findCompletedByUserIdAndIds(anyLong(), any())).thenReturn(List.of(interview));
        when(scoreMapper.findByInterviewIds(any())).thenReturn(List.of());

        InterviewReport report = generator.generate(1L, List.of(1L));

        assertEquals(1, report.trend().points().size());
        assertEquals("2024-01-01", report.trend().points().get(0).date());
        assertEquals(75, report.trend().points().get(0).score());
    }

    @Test
    void getAvailableInterviews_returnsList() {
        ReportInterviewRecord interview = createInterview(10L, 85);
        interview.setCompletedAt(LocalDateTime.now());
        when(recordMapper.findCompletedByUserIdAndDateRange(anyLong(), any(), any())).thenReturn(List.of(interview));

        List<InterviewOption> options = generator.getAvailableInterviews(1L);

        assertEquals(1, options.size());
        assertEquals(10L, options.get(0).id());
        assertEquals("ALIBABA", options.get(0).company());
    }

    @Test
    void generate_withQuestionScores_populatesStrongWeakAreas() {
        ReportInterviewRecord interview = createInterview(1L, 70);
        interview.setScoreTech(50);
        interview.setScoreExpression(90);
        when(recordMapper.findCompletedByUserIdAndIds(anyLong(), any())).thenReturn(List.of(interview));

        ReportQuestionScore score = new ReportQuestionScore();
        score.setId(1L);
        score.setInterviewId(1L);
        score.setQuestionType("TECH_BASIC");
        score.setQuestionDescription("什么是JVM？");
        score.setScore(40);
        score.setFeedback("回答不完整");
        when(scoreMapper.findByInterviewIds(any())).thenReturn(List.of(score));

        InterviewReport report = generator.generate(1L, List.of(1L));

        assertFalse(report.strongAreas().insights().isEmpty());
        assertFalse(report.weakAreas().insights().isEmpty());
    }

    @Test
    void generate_twoInterviews_trendSortedAscending() {
        ReportInterviewRecord earlier = createInterview(1L, 70);
        earlier.setCompletedAt(LocalDateTime.of(2024, 1, 1, 10, 0));
        ReportInterviewRecord later = createInterview(2L, 85);
        later.setCompletedAt(LocalDateTime.of(2024, 1, 2, 10, 0));
        when(recordMapper.findCompletedByUserIdAndIds(anyLong(), any())).thenReturn(List.of(earlier, later));
        when(scoreMapper.findByInterviewIds(any())).thenReturn(List.of());

        InterviewReport report = generator.generate(1L, List.of(1L, 2L));

        List<TrendPoint> points = report.trend().points();
        assertEquals(2, points.size());
        assertEquals("2024-01-01", points.get(0).date());
        assertEquals("2024-01-02", points.get(1).date());
    }

    @Test
    void generate_nullDimensionScores_noNpe() {
        ReportInterviewRecord interview = createInterview(1L, 70);
        interview.setScoreTech(null);
        interview.setScoreExpression(null);
        interview.setScoreProject(null);
        interview.setScoreLogic(null);
        interview.setScoreAdaptability(null);
        when(recordMapper.findCompletedByUserIdAndIds(anyLong(), any())).thenReturn(List.of(interview));
        when(scoreMapper.findByInterviewIds(any())).thenReturn(List.of());

        InterviewReport report = generator.generate(1L, List.of(1L));

        assertEquals(5, report.dimensionScores().dimensions().size());
        assertTrue(report.dimensionScores().dimensions().stream()
                .allMatch(d -> d.score() == 0.0));
    }

    @Test
    void generate_nullQuestionType_filteredOut() {
        ReportInterviewRecord interview = createInterview(1L, 70);
        when(recordMapper.findCompletedByUserIdAndIds(anyLong(), any())).thenReturn(List.of(interview));

        ReportQuestionScore score = new ReportQuestionScore();
        score.setId(1L);
        score.setInterviewId(1L);
        score.setQuestionType(null);
        score.setQuestionDescription("test");
        score.setScore(50);
        score.setFeedback("ok");
        when(scoreMapper.findByInterviewIds(any())).thenReturn(List.of(score));

        InterviewReport report = generator.generate(1L, List.of(1L));

        assertFalse(report.strongAreas().insights().isEmpty());
        assertFalse(report.weakAreas().insights().isEmpty());
    }

    @Test
    void generate_identicalDimensionScores_rankingStable() {
        ReportInterviewRecord interview = createInterview(1L, 80);
        interview.setScoreTech(70);
        interview.setScoreExpression(70);
        interview.setScoreProject(70);
        interview.setScoreLogic(70);
        interview.setScoreAdaptability(70);
        when(recordMapper.findCompletedByUserIdAndIds(anyLong(), any())).thenReturn(List.of(interview));
        when(scoreMapper.findByInterviewIds(any())).thenReturn(List.of());

        InterviewReport report = generator.generate(1L, List.of(1L));

        List<DimensionScore> dimensions = report.dimensionScores().dimensions();
        assertEquals(5, dimensions.size());
        assertTrue(dimensions.stream().allMatch(d -> d.score() == 70.0));
        assertEquals(1, dimensions.get(0).rank());
        assertEquals(2, dimensions.get(1).rank());
        assertEquals(3, dimensions.get(2).rank());
        assertEquals(4, dimensions.get(3).rank());
        assertEquals(5, dimensions.get(4).rank());
    }

    @Test
    void getAvailableInterviews_emptyList() {
        when(recordMapper.findCompletedByUserIdAndDateRange(anyLong(), any(), any())).thenReturn(List.of());

        List<InterviewOption> options = generator.getAvailableInterviews(1L);

        assertTrue(options.isEmpty());
    }

    @Test
    void getAvailableInterviews_sortedByCompletedAtDescending() {
        ReportInterviewRecord oldest = createInterview(1L, 70);
        oldest.setCompletedAt(LocalDateTime.of(2024, 1, 1, 10, 0));
        ReportInterviewRecord newest = createInterview(2L, 85);
        newest.setCompletedAt(LocalDateTime.of(2024, 1, 2, 10, 0));
        doReturn(List.of(newest, oldest)).when(recordMapper).findCompletedByUserIdAndDateRange(anyLong(), any(), any());

        List<InterviewOption> options = generator.getAvailableInterviews(1L);

        assertEquals(2, options.size());
        assertEquals(2L, options.get(0).id());
        assertEquals(1L, options.get(1).id());
    }

    @Test
    void generate_allScoreTotalNull_summaryDefaultsToZero() {
        ReportInterviewRecord interview = createInterview(1L, 0);
        interview.setScoreTotal(null);
        interview.setDurationSeconds(null);
        when(recordMapper.findCompletedByUserIdAndIds(anyLong(), any())).thenReturn(List.of(interview));
        when(scoreMapper.findByInterviewIds(any())).thenReturn(List.of());

        InterviewReport report = generator.generate(1L, List.of(1L));

        assertEquals(0, report.summary().avgScore(), 0.1);
        assertEquals(0, report.summary().highestScore());
        assertEquals(0, report.summary().lowestScore());
        assertEquals(0, report.summary().totalDurationSeconds());
    }

    @Test
    void generate_allDimensionsSameScore_strongWeakStillTwoEach() {
        ReportInterviewRecord interview = createInterview(1L, 70);
        interview.setScoreTech(70);
        interview.setScoreExpression(70);
        interview.setScoreProject(70);
        interview.setScoreLogic(70);
        interview.setScoreAdaptability(70);
        when(recordMapper.findCompletedByUserIdAndIds(anyLong(), any())).thenReturn(List.of(interview));
        when(scoreMapper.findByInterviewIds(any())).thenReturn(List.of());

        InterviewReport report = generator.generate(1L, List.of(1L));

        assertEquals(2, report.strongAreas().insights().size());
        assertEquals(2, report.weakAreas().insights().size());
    }

    @Test
    void generate_emptyQuestionScores_insightsStillPresent() {
        ReportInterviewRecord interview = createInterview(1L, 70);
        interview.setScoreTech(50);
        interview.setScoreExpression(90);
        when(recordMapper.findCompletedByUserIdAndIds(anyLong(), any())).thenReturn(List.of(interview));
        when(scoreMapper.findByInterviewIds(any())).thenReturn(List.of());

        InterviewReport report = generator.generate(1L, List.of(1L));

        assertEquals(2, report.strongAreas().insights().size());
        assertEquals(2, report.weakAreas().insights().size());
        assertTrue(report.strongAreas().insights().get(0).questions().isEmpty());
        assertTrue(report.weakAreas().insights().get(0).questions().isEmpty());
    }

    private ReportInterviewRecord createInterview(Long id, int scoreTotal) {
        ReportInterviewRecord record = new ReportInterviewRecord();
        record.setId(id);
        record.setUserId(1L);
        record.setCompany("ALIBABA");
        record.setPosition("BACKEND");
        record.setStatus("COMPLETED");
        record.setScoreTotal(scoreTotal);
        record.setDurationSeconds(1800);
        record.setCompletedAt(LocalDateTime.now());
        return record;
    }
}
