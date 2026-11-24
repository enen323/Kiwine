package com.prepdeck.report.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.prepdeck.report.domain.ReportInterviewRecord;

import java.time.LocalDateTime;
import java.util.List;

public interface ReportInterviewRecordMapper extends BaseMapper<ReportInterviewRecord> {

    default List<ReportInterviewRecord> findCompletedByUserIdAndDateRange(Long userId, LocalDateTime start, LocalDateTime end) {
        LambdaQueryWrapper<ReportInterviewRecord> wrapper = new LambdaQueryWrapper<ReportInterviewRecord>()
                .eq(ReportInterviewRecord::getUserId, userId)
                .eq(ReportInterviewRecord::getStatus, "COMPLETED");
        if (start != null) {
            wrapper.ge(ReportInterviewRecord::getCompletedAt, start);
        }
        if (end != null) {
            wrapper.le(ReportInterviewRecord::getCompletedAt, end);
        }
        wrapper.orderByDesc(ReportInterviewRecord::getCompletedAt);
        return selectList(wrapper);
    }

    default List<ReportInterviewRecord> findCompletedByUserIdAndIds(Long userId, List<Long> ids) {
        return selectList(new LambdaQueryWrapper<ReportInterviewRecord>()
                .eq(ReportInterviewRecord::getUserId, userId)
                .in(ReportInterviewRecord::getId, ids)
                .eq(ReportInterviewRecord::getStatus, "COMPLETED")
                .orderByDesc(ReportInterviewRecord::getCompletedAt));
    }
}
