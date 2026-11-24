package com.prepdeck.report.service;

import com.prepdeck.report.domain.ReportInterviewRecord;
import com.prepdeck.report.domain.ReportQuestionScore;
import com.prepdeck.report.dto.response.*;
import com.prepdeck.report.repository.ReportInterviewRecordMapper;
import com.prepdeck.report.repository.ReportQuestionScoreMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
public class ReportGeneratorImpl implements ReportGenerator {

    private static final Map<String, String> DIMENSION_NAMES = Map.of(
            "scoreTech", "技术深度",
            "scoreExpression", "表达能力",
            "scoreProject", "项目经验",
            "scoreLogic", "逻辑思维",
            "scoreAdaptability", "应变能力"
    );

    private static final List<String> DIMENSION_FIELDS = List.of(
            "scoreTech", "scoreExpression", "scoreProject", "scoreLogic", "scoreAdaptability"
    );

    private static final Map<String, String> SUGGESTION_TEMPLATES = Map.of(
            "技术深度", "建议加强技术基础学习，重点补充算法、系统设计相关知识，多做实战练习。",
            "表达能力", "建议多进行模拟面试练习，注意回答结构清晰、逻辑严密，控制语速和条理。",
            "项目经验", "建议深入梳理项目经历，用STAR法则准备项目介绍，突出个人贡献和技术难点。",
            "逻辑思维", "建议多做逻辑推理和案例分析练习，回答时先给出结论再展开论述。",
            "应变能力", "建议多参加模拟面试，提高临场应变能力，遇到不会的问题学会拆解和引导。"
    );

    private static final Map<String, List<String>> DIMENSION_QUESTION_TYPES = Map.of(
            "技术深度", List.of("TECH_BASIC", "ALGORITHM", "SYSTEM_DESIGN", "TECH_PROJECT"),
            "表达能力", List.of("SELF_INTRO", "BEHAVIORAL"),
            "项目经验", List.of("TECH_PROJECT"),
            "逻辑思维", List.of("TECH_BASIC", "ALGORITHM", "SYSTEM_DESIGN"),
            "应变能力", List.of("BEHAVIORAL", "REVERSE_QA")
    );

    private final ReportInterviewRecordMapper recordMapper;
    private final ReportQuestionScoreMapper scoreMapper;

    @Override
    public InterviewReport generate(Long userId, List<Long> interviewIds) {
        List<ReportInterviewRecord> interviews;
        if (interviewIds != null && !interviewIds.isEmpty()) {
            interviews = recordMapper.findCompletedByUserIdAndIds(userId, interviewIds);
        } else {
            interviews = recordMapper.findCompletedByUserIdAndDateRange(userId, null, null);
        }

        if (interviews.isEmpty()) {
            return emptyReport();
        }

        List<Long> ids = interviews.stream()
                .map(ReportInterviewRecord::getId)
                .toList();
        List<ReportQuestionScore> scores = scoreMapper.findByInterviewIds(ids);

        OverallSummary summary = computeSummary(interviews);
        List<DimensionScore> dimensionScores = computeDimensionScores(interviews);
        StrongAreas strongAreas = computeStrongAreas(dimensionScores, scores);
        WeakAreas weakAreas = computeWeakAreas(dimensionScores, scores);
        TrendData trend = computeTrend(interviews);
        ImprovementSuggestions suggestions = computeSuggestions(weakAreas);

        return new InterviewReport(summary, new DimensionScores(dimensionScores), strongAreas, weakAreas, trend, suggestions);
    }

    @Override
    public List<InterviewOption> getAvailableInterviews(Long userId) {
        List<ReportInterviewRecord> interviews = recordMapper.findCompletedByUserIdAndDateRange(userId, null, null);
        return interviews.stream()
                .map(i -> new InterviewOption(
                        i.getId(),
                        i.getCompany(),
                        i.getPosition(),
                        i.getCompletedAt(),
                        i.getScoreTotal()
                ))
                .toList();
    }

    private OverallSummary computeSummary(List<ReportInterviewRecord> interviews) {
        int total = interviews.size();
        double avg = interviews.stream().mapToInt(i -> safeInt(i.getScoreTotal())).average().orElse(0);
        int max = interviews.stream().mapToInt(i -> safeInt(i.getScoreTotal())).max().orElse(0);
        int min = interviews.stream().mapToInt(i -> safeInt(i.getScoreTotal())).min().orElse(0);
        int duration = interviews.stream().mapToInt(i -> safeInt(i.getDurationSeconds())).sum();
        return new OverallSummary(total, Math.round(avg * 10) / 10.0, max, min, duration);
    }

    private List<DimensionScore> computeDimensionScores(List<ReportInterviewRecord> interviews) {
        List<DimensionScore> scores = new ArrayList<>();
        for (String field : DIMENSION_FIELDS) {
            double avg = interviews.stream()
                    .mapToInt(i -> getFieldScore(i, field))
                    .average()
                    .orElse(0);
            scores.add(new DimensionScore(DIMENSION_NAMES.get(field), Math.round(avg * 10) / 10.0, 0));
        }
        scores.sort((a, b) -> Double.compare(b.score(), a.score()));
        for (int i = 0; i < scores.size(); i++) {
            scores.set(i, new DimensionScore(scores.get(i).name(), scores.get(i).score(), i + 1));
        }
        return scores;
    }

    private int getFieldScore(ReportInterviewRecord record, String field) {
        if (record == null) return 0;
        return switch (field) {
            case "scoreTech" -> safeInt(record.getScoreTech());
            case "scoreExpression" -> safeInt(record.getScoreExpression());
            case "scoreProject" -> safeInt(record.getScoreProject());
            case "scoreLogic" -> safeInt(record.getScoreLogic());
            case "scoreAdaptability" -> safeInt(record.getScoreAdaptability());
            default -> 0;
        };
    }

    private StrongAreas computeStrongAreas(List<DimensionScore> dimensionScores, List<ReportQuestionScore> allScores) {
        List<DimensionScore> topDims = dimensionScores.stream()
                .sorted((a, b) -> Double.compare(b.score(), a.score()))
                .limit(2)
                .toList();
        return new StrongAreas(topDims.stream()
                .map(d -> buildAreaInsight(d, allScores, true))
                .toList());
    }

    private WeakAreas computeWeakAreas(List<DimensionScore> dimensionScores, List<ReportQuestionScore> allScores) {
        List<DimensionScore> bottomDims = dimensionScores.stream()
                .sorted((a, b) -> Double.compare(a.score(), b.score()))
                .limit(2)
                .toList();
        return new WeakAreas(bottomDims.stream()
                .map(d -> buildAreaInsight(d, allScores, false))
                .toList());
    }

    private AreaInsight buildAreaInsight(DimensionScore dimension, List<ReportQuestionScore> allScores, boolean isStrong) {
        List<String> relatedTypes = DIMENSION_QUESTION_TYPES.getOrDefault(dimension.name(), List.of());
        List<QuestionInsight> questions = allScores.stream()
                .filter(s -> relatedTypes.isEmpty() || (s.getQuestionType() != null && relatedTypes.contains(s.getQuestionType())))
                .sorted((a, b) -> isStrong ? Integer.compare(safeInt(b.getScore()), safeInt(a.getScore())) : Integer.compare(safeInt(a.getScore()), safeInt(b.getScore())))
                .limit(3)
                .map(s -> new QuestionInsight(
                        s.getQuestionDescription() != null ? s.getQuestionDescription() : "",
                        safeInt(s.getScore()),
                        s.getFeedback() != null ? s.getFeedback() : ""
                ))
                .toList();
        return new AreaInsight(dimension.name(), dimension.score(), questions);
    }

    private TrendData computeTrend(List<ReportInterviewRecord> interviews) {
        List<ReportInterviewRecord> sorted = interviews.stream()
                .filter(i -> i.getCompletedAt() != null)
                .sorted(Comparator.comparing(ReportInterviewRecord::getCompletedAt))
                .toList();

        List<TrendPoint> points = sorted.stream()
                .map(i -> new TrendPoint(
                        i.getCompletedAt().toLocalDate().toString(),
                        safeInt(i.getScoreTotal())
                ))
                .toList();
        return new TrendData(points);
    }

    private ImprovementSuggestions computeSuggestions(WeakAreas weakAreas) {
        List<ImprovementSuggestion> items = weakAreas.insights().stream()
                .map(insight -> {
                    String template = SUGGESTION_TEMPLATES.getOrDefault(insight.dimension(),
                            "建议针对薄弱环节多做针对性练习。");
                    return new ImprovementSuggestion(insight.dimension(), template);
                })
                .toList();
        return new ImprovementSuggestions(items);
    }

    private InterviewReport emptyReport() {
        return new InterviewReport(
                new OverallSummary(0, 0, 0, 0, 0),
                new DimensionScores(List.of()),
                new StrongAreas(List.of()),
                new WeakAreas(List.of()),
                new TrendData(List.of()),
                new ImprovementSuggestions(List.of())
        );
    }

    private int safeInt(Integer value) {
        return value != null ? value : 0;
    }
}