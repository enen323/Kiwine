package com.prepdeck.interview.service;

import com.prepdeck.interview.dto.request.StartInterviewRequest;
import com.prepdeck.interview.dto.request.SubmitAnswerRequest;
import com.prepdeck.interview.dto.response.InterviewVO;
import com.prepdeck.interview.dto.response.InterviewListVO;
import com.prepdeck.interview.dto.response.InterviewMessageVO;
import com.prepdeck.interview.dto.response.StartInterviewResponse;
import com.prepdeck.interview.enums.ControlCommand;
import com.prepdeck.common.model.PageResult;
import com.prepdeck.common.dto.UserPracticeSummaryVO;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 面试服务接口
 *
 * <p>面试会话生命周期管理、对话处理、状态流转。</p>
 */
public interface InterviewService {

    /**
     * 开始一场新面试
     *
     * @param userId  当前用户 ID
     * @param request 开始面试请求（包含公司、岗位等信息）
     * @return 创建结果（含面试信息 + 第一道 AI 问题）
     */
    StartInterviewResponse startInterview(Long userId, StartInterviewRequest request);

    /**
     * 提交回答
     *
     * @param userId      当前用户 ID
     * @param interviewId 面试记录 ID
     * @param request     提交回答请求（包含回答内容）
     * @return SSE 发射器，异步推送 AI 回复
     */
    SseEmitter submitAnswer(Long userId, Long interviewId, SubmitAnswerRequest request);

    /**
     * 获取面试详情
     *
     * @param userId      当前用户 ID
     * @param interviewId 面试记录 ID
     * @return 面试视图对象
     */
    InterviewVO getInterview(Long userId, Long interviewId);

    /**
     * 获取用户面试列表（分页）
     *
     * @param userId    当前用户 ID
     * @param company   公司筛选（可选）
     * @param status    状态筛选（可选）
     * @param page      页码
     * @param pageSize  每页大小
     * @return 分页结果
     */
    PageResult<InterviewListVO> listUserInterviews(Long userId, String company, String status, int page, int pageSize);

    /**
     * 获取面试消息记录
     *
     * @param userId      当前用户 ID
     * @param interviewId 面试记录 ID
     * @return 消息视图对象列表
     */
    List<InterviewMessageVO> getMessages(Long userId, Long interviewId);

    /**
     * 终止面试
     *
     * @param userId      当前用户 ID
     * @param interviewId 面试记录 ID
     */
    void terminateInterview(Long userId, Long interviewId);

    /**
     * 删除面试记录（逻辑删除）及其消息
     *
     * @param userId      当前用户 ID
     * @param interviewId 面试记录 ID
     */
    void deleteInterview(Long userId, Long interviewId);

    /**
     * 控制指令（跳过/提示/提前结束）
     *
     * @param userId      当前用户 ID
     * @param interviewId 面试记录 ID
     * @param command     控制指令
     * @return SSE 发射器
     */
    SseEmitter controlInterview(Long userId, Long interviewId, ControlCommand command);

    /**
     * 获取用户面试实践摘要（供推荐引擎调用）
     *
     * @param userId              当前用户 ID
     * @param recentInterviewLimit 最近面试条数
     * @param practicedLimit       已做题目条数
     * @return 用户面试得分 + 已做题目描述列表
     */
    UserPracticeSummaryVO getUserPracticeSummary(Long userId, int recentInterviewLimit, int practicedLimit);
}
