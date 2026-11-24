package com.prepdeck.interview.controller;

import com.prepdeck.common.model.PageResult;
import com.prepdeck.interview.dto.request.StartInterviewRequest;
import com.prepdeck.interview.dto.request.SubmitAnswerRequest;
import com.prepdeck.interview.dto.response.InterviewListVO;
import com.prepdeck.interview.dto.response.InterviewMessageVO;
import com.prepdeck.interview.dto.response.InterviewVO;
import com.prepdeck.interview.dto.response.StartInterviewResponse;
import com.prepdeck.interview.enums.CompanyEnum;
import com.prepdeck.interview.enums.MessageRoleEnum;
import com.prepdeck.interview.enums.PositionEnum;
import com.prepdeck.interview.enums.QuestionTypeEnum;
import com.prepdeck.interview.repository.InterviewMapper;
import com.prepdeck.interview.repository.InterviewMessageMapper;
import com.prepdeck.interview.repository.InterviewQuestionScoreMapper;
import com.prepdeck.interview.service.InterviewService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InterviewController.class)
@TestPropertySource(properties = "spring.cloud.nacos.discovery.enabled=false")
class InterviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InterviewService interviewService;

    @MockBean
    private InterviewMapper interviewMapper;

    @MockBean
    private InterviewMessageMapper interviewMessageMapper;

    @MockBean
    private InterviewQuestionScoreMapper interviewQuestionScoreMapper;

    @Test
    void health_returnsRunning() throws Exception {
        mockMvc.perform(get("/api/v1/interviews/health")
                        .header("X-User-Id", "1")
                        .header("X-Gateway-Source", "prepdeck-gateway")
                .header("X-Gateway-Secret", "test-secret")
                        .header("X-Gateway-Source", "prepdeck-gateway")
                .header("X-Gateway-Secret", "test-secret"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value("interview-service is running"));
    }

    @Test
    void startInterview_success_returns200WithResponse() throws Exception {
        InterviewVO interviewVO = new InterviewVO(
                100L, 1L, "ALIBABA", CompanyEnum.ALIBABA.getDisplayName(),
                "BACKEND", PositionEnum.BACKEND.getDisplayName(),
                com.prepdeck.interview.enums.InterviewStatusEnum.CREATED,
                com.prepdeck.interview.enums.InterviewStageEnum.SELF_INTRO,
                0, 0, null, null, null, null, null, null, null,
                null, null, 0, LocalDateTime.now()
        );
        InterviewMessageVO messageVO = new InterviewMessageVO(
                200L, 100L, MessageRoleEnum.AI, "请做自我介绍",
                QuestionTypeEnum.SELF_INTRO, 1, 0, LocalDateTime.now()
        );
        StartInterviewResponse responseBody = new StartInterviewResponse(interviewVO, messageVO, null);
        when(interviewService.startInterview(anyLong(), any())).thenReturn(responseBody);

        mockMvc.perform(post("/api/v1/interviews")
                        .header("X-User-Id", "1")
                        .header("X-Gateway-Source", "prepdeck-gateway")
                .header("X-Gateway-Secret", "test-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyType\":\"ALIBABA\",\"position\":\"BACKEND\"}"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.interview.id").value(100))
                .andExpect(jsonPath("$.data.firstQuestion.content").value("请做自我介绍"))
                .andExpect(jsonPath("$.data.firstQuestion.sequenceNum").value(1));

        verify(interviewService).startInterview(eq(1L), any());
    }

    @Test
    void startInterview_invalidRequestBody_returns200WithError() throws Exception {
        mockMvc.perform(post("/api/v1/interviews")
                        .header("X-User-Id", "1")
                        .header("X-Gateway-Source", "prepdeck-gateway")
                .header("X-Gateway-Secret", "test-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyType\":\"ALIBABA\"}"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(com.prepdeck.common.enums.ErrorCode.INTERNAL_ERROR.getCode()));
    }

    @Test
    void startInterview_serviceThrows_returns200WithError() throws Exception {
        when(interviewService.startInterview(anyLong(), any()))
                .thenThrow(new com.prepdeck.common.exception.BusinessException(com.prepdeck.common.enums.ErrorCode.QUOTA_EXHAUSTED));

        mockMvc.perform(post("/api/v1/interviews")
                        .header("X-User-Id", "1")
                        .header("X-Gateway-Source", "prepdeck-gateway")
                .header("X-Gateway-Secret", "test-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyType\":\"ALIBABA\",\"position\":\"BACKEND\"}"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(com.prepdeck.common.enums.ErrorCode.QUOTA_EXHAUSTED.getCode()));
    }

    @Test
    void getInterview_success_returnsInterview() throws Exception {
        InterviewVO vo = new InterviewVO(
                100L, 1L, "ALIBABA", CompanyEnum.ALIBABA.getDisplayName(),
                "BACKEND", PositionEnum.BACKEND.getDisplayName(),
                com.prepdeck.interview.enums.InterviewStatusEnum.IN_PROGRESS,
                com.prepdeck.interview.enums.InterviewStageEnum.SELF_INTRO,
                0, 2, null, null, null, null, null, null, null,
                LocalDateTime.now().minusMinutes(10), null, 0, LocalDateTime.now()
        );
        when(interviewService.getInterview(eq(1L), eq(100L))).thenReturn(vo);

        mockMvc.perform(get("/api/v1/interviews/100")
                        .header("X-User-Id", "1")
                        .header("X-Gateway-Source", "prepdeck-gateway")
                .header("X-Gateway-Secret", "test-secret"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(100))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
    }

    @Test
    void getInterview_serviceThrows_returns200WithErrorCode() throws Exception {
        when(interviewService.getInterview(anyLong(), anyLong()))
                .thenThrow(new com.prepdeck.common.exception.NotFoundException(com.prepdeck.common.enums.ErrorCode.INTERVIEW_NOT_FOUND));

        mockMvc.perform(get("/api/v1/interviews/999")
                        .header("X-User-Id", "1")
                        .header("X-Gateway-Source", "prepdeck-gateway")
                .header("X-Gateway-Secret", "test-secret"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(com.prepdeck.common.enums.ErrorCode.INTERVIEW_NOT_FOUND.getCode()));
    }

    @Test
    void listInterviews_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/interviews")
                        .header("X-User-Id", "1")
                        .header("X-Gateway-Source", "prepdeck-gateway")
                .header("X-Gateway-Secret", "test-secret")
                        .param("page", "1")
                        .param("pageSize", "10"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void getMessages_success_returnsMessages() throws Exception {
        InterviewMessageVO msg = new InterviewMessageVO(
                1L, 100L, MessageRoleEnum.AI, "Hello",
                QuestionTypeEnum.SELF_INTRO, 1, 0, LocalDateTime.now()
        );
        when(interviewService.getMessages(eq(1L), eq(100L))).thenReturn(List.of(msg));

        mockMvc.perform(get("/api/v1/interviews/100/messages")
                        .header("X-User-Id", "1")
                        .header("X-Gateway-Source", "prepdeck-gateway")
                .header("X-Gateway-Secret", "test-secret"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].content").value("Hello"));
    }

    @Test
    void getMessages_serviceThrows_returns200WithErrorCode() throws Exception {
        when(interviewService.getMessages(anyLong(), anyLong()))
                .thenThrow(new com.prepdeck.common.exception.NotFoundException(com.prepdeck.common.enums.ErrorCode.INTERVIEW_NOT_FOUND));

        mockMvc.perform(get("/api/v1/interviews/999/messages")
                        .header("X-User-Id", "1")
                        .header("X-Gateway-Source", "prepdeck-gateway")
                .header("X-Gateway-Secret", "test-secret"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(com.prepdeck.common.enums.ErrorCode.INTERVIEW_NOT_FOUND.getCode()));
    }

    @Test
    void terminateInterview_success_returns200() throws Exception {
        doNothing().when(interviewService).terminateInterview(anyLong(), anyLong());

        mockMvc.perform(post("/api/v1/interviews/100/terminate")
                        .header("X-User-Id", "1")
                        .header("X-Gateway-Source", "prepdeck-gateway")
                .header("X-Gateway-Secret", "test-secret"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(interviewService).terminateInterview(eq(1L), eq(100L));
    }

    @Test
    void deleteInterview_success_returns200() throws Exception {
        doNothing().when(interviewService).deleteInterview(anyLong(), anyLong());

        mockMvc.perform(delete("/api/v1/interviews/100")
                        .header("X-User-Id", "1")
                        .header("X-Gateway-Source", "prepdeck-gateway")
                .header("X-Gateway-Secret", "test-secret"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(interviewService).deleteInterview(eq(1L), eq(100L));
    }
}
