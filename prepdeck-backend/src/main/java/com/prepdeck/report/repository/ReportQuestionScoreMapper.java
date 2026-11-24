package com.prepdeck.report.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.prepdeck.report.domain.ReportQuestionScore;

import java.util.List;

public interface ReportQuestionScoreMapper extends BaseMapper<ReportQuestionScore> {

    default List<ReportQuestionScore> findByInterviewIds(List<Long> interviewIds) {
        if (interviewIds == null || interviewIds.isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapper<ReportQuestionScore>()
                .in(ReportQuestionScore::getInterviewId, interviewIds)
                .orderByDesc(ReportQuestionScore::getCreateTime));
    }
}
