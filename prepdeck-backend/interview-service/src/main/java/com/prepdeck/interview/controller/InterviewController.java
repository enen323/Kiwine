package com.prepdeck.interview.controller;

import com.prepdeck.common.model.PageResult;
import com.prepdeck.common.model.Result;
import com.prepdeck.common.dto.UserPracticeSummaryVO;
import com.prepdeck.common.util.SecurityContext;
import com.prepdeck.interview.dto.request.StartInterviewRequest;
import com.prepdeck.interview.dto.request.SubmitAnswerRequest;
import com.prepdeck.interview.dto.request.ControlCommandRequest;
import com.prepdeck.interview.dto.response.InterviewListVO;
import com.prepdeck.interview.dto.response.InterviewMessageVO;
import com.prepdeck.interview.dto.response.InterviewVO;
import com.prepdeck.interview.dto.response.StartInterviewResponse;
import com.prepdeck.interview.service.InterviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 面试控制器
 *
 * <p>面试会话管理接口：开始面试、提交回答、查询详情、终止面试。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/interviews")
@RequiredArgsConstructor
public class InterviewController {

    private final InterviewService interviewService;

    /**
     * 开始一场新面试
     *
     * <p>创建面试记录并生成第一道 AI 问题。</p>
     */
    @PostMapping
    public Result<StartInterviewResponse> startInterview(@Valid @RequestBody StartInterviewRequest request) {
        Long userId = SecurityContext.getCurrentUserId();
        StartInterviewResponse response = interviewService.startInterview(userId, request);
        log.info("用户开始面试: userId={}, company={}, position={}",
                userId, request.companyType(), request.position());
        return Result.success(response);
    }

    /**
     * 提交回答
     *
     * @param id 面试记录 ID
     */
    @PostMapping("/{id}/answer")
    public SseEmitter submitAnswer(
            @PathVariable Long id,
            @Valid @RequestBody SubmitAnswerRequest request) {
        Long userId = SecurityContext.getCurrentUserId();
        return interviewService.submitAnswer(userId, id, request);
    }

    /**
     * 获取面试详情
     *
     * @param id 面试记录 ID
     */
    @GetMapping("/{id}")
    public Result<InterviewVO> getInterview(@PathVariable Long id) {
        Long userId = SecurityContext.getCurrentUserId();
        InterviewVO vo = interviewService.getInterview(userId, id);
        return Result.success(vo);
    }

    /**
     * 获取面试列表（分页）
     *
     * @param company  公司筛选（可选）
     * @param status   状态筛选（可选）
     * @param page     页码
     * @param pageSize 每页大小
     */
    @GetMapping
    public Result<PageResult<InterviewListVO>> listInterviews(
            @RequestParam(required = false) String company,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        Long userId = SecurityContext.getCurrentUserId();
        PageResult<InterviewListVO> pageResult = interviewService.listUserInterviews(
                userId, company, status, page, pageSize);
        return Result.success(pageResult);
    }

    /**
     * 获取面试消息记录
     *
     * @param id 面试记录 ID
     */
    @GetMapping("/{id}/messages")
    public Result<List<InterviewMessageVO>> getMessages(@PathVariable Long id) {
        Long userId = SecurityContext.getCurrentUserId();
        List<InterviewMessageVO> messages = interviewService.getMessages(userId, id);
        return Result.success(messages);
    }

    /**
     * 获取用户面试实践摘要（供推荐引擎使用）
     *
     * @param recentInterviewLimit 最近面试条数
     * @param practicedLimit       已做题目条数
     */
    @GetMapping("/user-summary")
    public Result<UserPracticeSummaryVO> getUserPracticeSummary(
            @RequestParam(required = false, defaultValue = "5") Integer recentInterviewLimit,
            @RequestParam(required = false, defaultValue = "50") Integer practicedLimit) {
        Long userId = SecurityContext.getCurrentUserId();
        return Result.success(interviewService.getUserPracticeSummary(userId, recentInterviewLimit, practicedLimit));
    }

    /**
     * 终止面试
     *
     * @param id 面试记录 ID
     */
    @PostMapping("/{id}/terminate")
    public Result<Void> terminateInterview(@PathVariable Long id) {
        Long userId = SecurityContext.getCurrentUserId();
        interviewService.terminateInterview(userId, id);
        return Result.success();
    }

    /**
     * 删除面试记录
     *
     * @param id 面试记录 ID
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteInterview(@PathVariable Long id) {
        Long userId = SecurityContext.getCurrentUserId();
        interviewService.deleteInterview(userId, id);
        return Result.success();
    }

    /**
     * 控制指令（跳过/提示/提前结束）
     *
     * @param id 面试记录 ID
     */
    @PostMapping("/{id}/control")
    public SseEmitter controlInterview(@PathVariable Long id, @Valid @RequestBody ControlCommandRequest request) {
        Long userId = SecurityContext.getCurrentUserId();
        return interviewService.controlInterview(userId, id, request.command());
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public Result<String> health() {
        return Result.success("interview-service is running");
    }
}
