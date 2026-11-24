package com.prepdeck.interview.dto.response;

import com.prepdeck.interview.dto.model.ResumeProfile;

/**
 * 开始面试响应
 */
public record StartInterviewResponse(
        InterviewVO interview,
        InterviewMessageVO firstQuestion,
        ResumeProfile resumeProfile
) {}
