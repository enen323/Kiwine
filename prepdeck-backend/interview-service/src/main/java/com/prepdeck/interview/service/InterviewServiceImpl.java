package com.prepdeck.interview.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.prepdeck.common.constant.ApiConstant;
import com.prepdeck.common.dto.QuotaCheckResult;
import com.prepdeck.common.enums.ErrorCode;
import com.prepdeck.common.exception.BadRequestException;
import com.prepdeck.common.exception.BusinessException;
import com.prepdeck.common.exception.NotFoundException;
import com.prepdeck.common.model.PageResult;
import com.prepdeck.common.util.SecurityContext;
import com.prepdeck.interview.domain.InterviewEntity;
import com.prepdeck.interview.domain.InterviewMessageEntity;
import com.prepdeck.interview.domain.InterviewQuestionScoreEntity;
import com.prepdeck.interview.dto.request.StartInterviewRequest;
import com.prepdeck.interview.dto.request.SubmitAnswerRequest;
import com.prepdeck.interview.dto.response.InterviewListVO;
import com.prepdeck.interview.dto.response.InterviewMessageVO;
import com.prepdeck.interview.dto.response.InterviewVO;
import com.prepdeck.interview.dto.response.StartInterviewResponse;
import com.prepdeck.interview.client.QuotaClient;
import com.prepdeck.interview.client.ResumeClient;
import com.prepdeck.interview.dto.model.ResumeProfile;
import com.prepdeck.interview.dto.model.ResumeProfileConverter;
import com.prepdeck.interview.enums.CompanyEnum;
import com.prepdeck.interview.enums.ControlCommand;
import com.prepdeck.interview.enums.InterviewStageEnum;
import com.prepdeck.interview.enums.InterviewStatusEnum;
import com.prepdeck.interview.enums.MessageRoleEnum;
import com.prepdeck.interview.enums.PositionEnum;
import com.prepdeck.interview.enums.QuestionTypeEnum;
import com.prepdeck.interview.repository.InterviewMapper;
import com.prepdeck.interview.repository.InterviewMessageMapper;
import com.prepdeck.interview.repository.InterviewQuestionScoreMapper;
import com.prepdeck.interview.service.engine.InterviewEngine;
import com.prepdeck.common.dto.UserPracticeSummaryVO;
import com.prepdeck.common.dto.UserInterviewScoreVO;
import com.prepdeck.resume.dto.response.ResumeVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import feign.FeignException;

import jakarta.servlet.http.HttpServletRequest;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 面试服务实现
 *
 * <p>管理面试会话的完整生命周期：创建、答题、状态流转、查询、终止。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InterviewServiceImpl implements InterviewService {

    private final InterviewMapper interviewMapper;
    private final InterviewMessageMapper messageMapper;
    private final InterviewQuestionScoreMapper questionScoreMapper;
    private final InterviewEngine interviewEngine;
    private final ResumeClient resumeClient;
    @Qualifier("interviewExecutor")
    private final Executor interviewExecutor;
    @Qualifier("interviewScheduler")
    private final ScheduledExecutorService interviewScheduler;
    private final QuotaClient quotaClient;
    private final StringRedisTemplate stringRedisTemplate;

    private static final String SUBMIT_LOCK_PREFIX = "interview:submit-lock:";
    private static final long SUBMIT_LOCK_TTL_SECONDS = 5;
    private static final String REQUEST_IDEMPOTENT_PREFIX = "interview:req:start:";
    private static final int MAX_SSE_CONNECTIONS = 500;
    private static final String SSE_PERMIT_KEY = "interview:sse:permit";

    private static final ConcurrentHashMap<Long, Queue<Runnable>> ASYNC_TASK_QUEUES = new ConcurrentHashMap<>();

    static {
        Thread cleanupThread = new Thread(() -> {
            while (true) {
                try {
                    TimeUnit.MINUTES.sleep(5);
                    ASYNC_TASK_QUEUES.entrySet().removeIf(entry -> entry.getValue().isEmpty());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "interview-async-queue-cleanup");
        cleanupThread.setDaemon(true);
        cleanupThread.start();
    }

    @Transactional(readOnly = false, rollbackFor = Exception.class)
    public InterviewEntity createInterviewRecord(Long userId, StartInterviewRequest request) {
        checkNoActiveInterview(userId);
        var entity = new InterviewEntity();
        entity.setUserId(userId);
        entity.setResumeId(request.resumeId());
        entity.setCompany(request.companyType().name());
        entity.setPosition(request.position().name());
        entity.setStatus(InterviewStatusEnum.CREATED);
        entity.setCurrentStage(InterviewStageEnum.SELF_INTRO);
        entity.setStageOrder(0);
        entity.setTotalQuestions(0);
        interviewMapper.insert(entity);
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StartInterviewResponse startInterview(Long userId, StartInterviewRequest request) {
        // 1. 先校验是否存在未完成面试：必须在扣减配额之前，避免「配额已扣但面试未创建」
        checkNoActiveInterview(userId);

        // 2. 幂等请求校验
        checkDuplicateRequest(getRequestId(), userId);

        // 3. 并发/原子锁：防止同一用户短时间内并发创建多场面试
        String startLockKey = "interview:start:" + userId;
        Boolean startLocked = stringRedisTemplate.opsForValue().setIfAbsent(startLockKey, "1", 30, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(startLocked)) {
            throw new BadRequestException("您有一个进行中的面试，请先完成或终止该面试");
        }
        try {
            // 4. 锁内二次校验：防止并发请求绕过点 1 的检查
            checkNoActiveInterview(userId);

            // 5. 所有前置校验通过后再扣减配额
            QuotaCheckResult checkResult = quotaClient.consume("INTERVIEW").data();
            if (checkResult == null || !checkResult.allowed()) {
                throw new BusinessException(ErrorCode.QUOTA_EXHAUSTED.getCode(),
                        checkResult != null ? checkResult.reason() : "QUOTA_EXHAUSTED");
            }

            InterviewEntity interview = createInterviewRecord(userId, request);

            ResumeProfile profile = null;
        if (request.resumeId() != null) {
            try {
                ResumeVO resumeVO = resumeClient.getById(request.resumeId());
                profile = ResumeProfileConverter.fromResumeVO(resumeVO);
            } catch (Exception e) {
                log.warn("加载简历失败: resumeId={}", request.resumeId(), e);
            }
        }
        InterviewMessageEntity firstMessage = interviewEngine.generateFirstQuestion(interview, profile);
        messageMapper.insert(firstMessage);

            InterviewVO interviewVO = toInterviewVO(interview);
            InterviewMessageVO messageVO = toMessageVO(firstMessage);

            return new StartInterviewResponse(interviewVO, messageVO, profile);
        } finally {
            // 无论成功或失败（配额不足/创建异常）都释放启动锁，避免锁残留
            stringRedisTemplate.delete(startLockKey);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SseEmitter submitAnswer(Long userId, Long interviewId, SubmitAnswerRequest request) {
        if (!acquireSsePermit(SSE_PERMIT_KEY)) {
            throw new BadRequestException("服务器连接数已达上限，请稍后再试");
        }
        InterviewEntity interview = findInterviewOrThrow(interviewId);
        verifyOwnership(interview, userId);
        verifyStatusForAnswer(interview);

        if (!acquireSubmitLock(interviewId)) {
            releaseSsePermit(SSE_PERMIT_KEY);
            throw new BadRequestException("请勿频繁提交，请等待上一条处理完成");
        }

        try {
            boolean firstAnswer = interview.getStatus() == InterviewStatusEnum.CREATED;
            if (firstAnswer) {
                interview.setStatus(InterviewStatusEnum.IN_PROGRESS);
                interview.setStartedAt(LocalDateTime.now());
                interviewMapper.updateById(interview);
            }

            var userMessage = new InterviewMessageEntity();
            userMessage.setInterviewId(interviewId);
            userMessage.setRole(MessageRoleEnum.USER);
            userMessage.setContent(request.content());
            userMessage.setTokenCount(0);

            var seqResult = getNextSequenceWithLock(userMessage);
            int nextSeq = seqResult.sequenceNum();
            log.info("用户回答已保存: interviewId={}, messageId={}, seq={}",
                    interviewId, userMessage.getId(), nextSeq);

            ResumeProfile profile = null;
            if (interview.getResumeId() != null) {
                try {
                    ResumeVO resumeVO = resumeClient.getById(interview.getResumeId());
                    profile = ResumeProfileConverter.fromResumeVO(resumeVO);
                } catch (Exception e) {
                    log.warn("加载简历失败: resumeId={}", interview.getResumeId(), e);
                }
            }

            SseEmitter emitter = new SseEmitter(300_000L);
            ResumeProfile finalProfile = profile;

            List<InterviewMessageEntity> history = getMessageHistory(interviewId);

            enqueueAsyncTask(interviewId, () ->
                    processAndStream(interview, finalProfile, userMessage, history, emitter, nextSeq));

            emitter.onCompletion(() -> {
                releaseSsePermit(SSE_PERMIT_KEY);
                dequeueAsyncTask(interviewId);
            });
            emitter.onError(e -> {
                releaseSsePermit(SSE_PERMIT_KEY);
                dequeueAsyncTask(interviewId);
            });
            emitter.onTimeout(() -> {
                releaseSsePermit(SSE_PERMIT_KEY);
                dequeueAsyncTask(interviewId);
            });

            return emitter;
        } finally {
            releaseSubmitLock(interviewId);
        }
    }

    private void enqueueAsyncTask(Long interviewId, Runnable task) {
        Queue<Runnable> queue = ASYNC_TASK_QUEUES.computeIfAbsent(interviewId, k -> new LinkedBlockingQueue<>());
        synchronized (queue) {
            queue.add(task);
            if (queue.size() == 1) {
                interviewExecutor.execute(task);
            }
        }
    }

    private void dequeueAsyncTask(Long interviewId) {
        Queue<Runnable> queue = ASYNC_TASK_QUEUES.get(interviewId);
        if (queue == null) return;
        Runnable next;
        synchronized (queue) {
            queue.poll();
            next = queue.peek();
        }
        if (next != null) {
            interviewExecutor.execute(next);
        }
    }

    // ponytail: test helper; clears inter-test state from ASYNC_TASK_QUEUES
    static void clearAsyncTaskQueuesForTest() {
        ASYNC_TASK_QUEUES.clear();
    }

    private boolean acquireSsePermit(String key) {
        Long count = stringRedisTemplate.opsForValue().increment(SSE_PERMIT_KEY);
        if (count != null && count <= MAX_SSE_CONNECTIONS) {
            return true;
        }
        stringRedisTemplate.opsForValue().decrement(SSE_PERMIT_KEY);
        return false;
    }

    private void releaseSsePermit(String key) {
        stringRedisTemplate.opsForValue().decrement(SSE_PERMIT_KEY);
    }

    private void processAndStream(
            InterviewEntity interview,
            ResumeProfile profile,
            InterviewMessageEntity userMessage,
            List<InterviewMessageEntity> history,
            SseEmitter emitter,
            int nextSeq) {
        final Long currentInterviewId = interview.getId();
        try {
            InterviewEngine.EngineResult engineResult = interviewEngine.processAnswerStream(
                    interview, profile, userMessage, history, emitter);

            int aiSeq = nextSeq + 1;
            var aiMessage = new InterviewMessageEntity();
            aiMessage.setInterviewId(interview.getId());
            aiMessage.setRole(MessageRoleEnum.AI);
            aiMessage.setContent(engineResult.aiResponse());
            aiMessage.setQuestionType(engineResult.questionType());
            aiMessage.setSequenceNum(aiSeq);
            aiMessage.setTokenCount(0);
            messageMapper.insert(aiMessage);
            log.info("AI 回复已保存: interviewId={}, messageId={}, seq={}",
                    interview.getId(), aiMessage.getId(), aiSeq);

            InterviewMessageEntity previousAiMessage = history.stream()
                    .filter(m -> m.getRole() == MessageRoleEnum.AI)
                    .filter(m -> m.getSequenceNum() != null && m.getSequenceNum().equals(nextSeq - 1))
                    .findFirst()
                    .orElse(aiMessage);
            interviewExecutor.execute(() -> asyncScoreQuestion(currentInterviewId, previousAiMessage, userMessage.getContent()));

            interview.setTotalQuestions(interview.getTotalQuestions() + 1);
            if (engineResult.stageCompleted() && engineResult.nextStage() != null) {
                InterviewStageEnum completedStage = interview.getCurrentStage();
                if (engineResult.interviewCompleted()) {
                    interview.setStatus(InterviewStatusEnum.COMPLETED);
                    interview.setCurrentStage(InterviewStageEnum.COMPLETED);
                    interview.setCompletedAt(LocalDateTime.now());
                    if (interview.getStartedAt() != null) {
                        interview.setDurationSeconds((int) ChronoUnit.SECONDS.between(
                                interview.getStartedAt(), interview.getCompletedAt()));
                    }
                    log.info("面试已完成，等待异步评分聚合: interviewId={}", interview.getId());
                } else {
                    interview.setCurrentStage(engineResult.nextStage());
                    interview.setStageOrder(interview.getStageOrder() + 1);
                }
                log.info("阶段流转: interviewId={}, newStage={}, stageOrder={}",
                        interview.getId(), engineResult.nextStage(), interview.getStageOrder());
                scheduleStageAggregation(interview.getId(), completedStage);
            }
            interviewMapper.updateById(interview);

            InterviewVO interviewVO = toInterviewVO(interview);
            InterviewMessageVO userAnswerVO = toMessageVO(userMessage);
            InterviewMessageVO aiResponseVO = toMessageVO(aiMessage);

            emitter.send(SseEmitter.event()
                    .name("ai_response")
                    .data(new SubmitAnswerSSEContent(
                            interviewVO, userAnswerVO, aiResponseVO,
                            engineResult.stageCompleted(), engineResult.interviewCompleted(),
                            engineResult.scoreTotal(), engineResult.suggestion())));

            heartbeat(emitter);
            emitter.complete();
            dequeueAsyncTask(currentInterviewId);
        } catch (FeignException e) {
            log.error("远程服务调用失败: interviewId={}, status={}, body={}",
                    currentInterviewId, e.status(), e.contentUTF8(), e);
            completeWithError(emitter, "AI 服务暂时不可用，请稍后重试");
        } catch (Exception e) {
            Throwable root = unwrapRootCause(e);
            if (root instanceof java.io.IOException) {
                log.debug("SSE 连接已断开，中止 AI 处理: interviewId={}", currentInterviewId, e);
            } else {
                log.error("处理回答失败: interviewId={}", currentInterviewId, e);
                completeWithError(emitter, "处理失败，请重试");
            }
        }
    }

    private static Throwable unwrapRootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private record SubmitAnswerSSEContent(
            InterviewVO interview,
            InterviewMessageVO userAnswer,
            InterviewMessageVO aiResponse,
            boolean stageCompleted,
            boolean interviewCompleted,
            Integer scoreTotal,
            String suggestion
    ) {}

    @Override
    @Transactional(readOnly = true)
    public InterviewVO getInterview(Long userId, Long interviewId) {
        InterviewEntity interview = findInterviewOrThrow(interviewId);
        verifyOwnership(interview, userId);
        return toInterviewVO(interview);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<InterviewListVO> listUserInterviews(Long userId, String company, String status,
                                                           int page, int pageSize) {
        if (pageSize > 50) pageSize = 50;
        var queryWrapper = new LambdaQueryWrapper<InterviewEntity>()
                .eq(InterviewEntity::getUserId, userId);

        if (company != null && !company.isBlank()) {
            queryWrapper.eq(InterviewEntity::getCompany, company);
        }
        if (status != null && !status.isBlank()) {
            queryWrapper.eq(InterviewEntity::getStatus, status);
        }

        queryWrapper.orderByDesc(InterviewEntity::getCreateTime);

        IPage<InterviewEntity> pageResult = interviewMapper.selectPage(
                new Page<>(page, pageSize), queryWrapper);

        List<InterviewListVO> list = pageResult.getRecords().stream()
                .map(this::toListVO)
                .toList();

        return PageResult.success(list, page, pageSize, pageResult.getTotal());
    }

    @Override
    @Transactional(readOnly = true)
    public List<InterviewMessageVO> getMessages(Long userId, Long interviewId) {
        InterviewEntity interview = findInterviewOrThrow(interviewId);
        verifyOwnership(interview, userId);

        List<InterviewMessageEntity> messages = getMessageHistory(interviewId);
        return messages.stream()
                .map(this::toMessageVO)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void terminateInterview(Long userId, Long interviewId) {
        InterviewEntity interview = findInterviewOrThrow(interviewId);
        verifyOwnership(interview, userId);

        if (interview.getStatus() == InterviewStatusEnum.COMPLETED
                || interview.getStatus() == InterviewStatusEnum.TERMINATED) {
            throw new BadRequestException(ErrorCode.INTERVIEW_ALREADY_COMPLETED);
        }

        interview.setStatus(InterviewStatusEnum.TERMINATED);
        interview.setCompletedAt(LocalDateTime.now());
        if (interview.getStartedAt() != null) {
            interview.setDurationSeconds((int) ChronoUnit.SECONDS.between(
                    interview.getStartedAt(), interview.getCompletedAt()));
        }
        interviewMapper.updateById(interview);

        log.info("面试已终止: interviewId={}, userId={}", interviewId, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteInterview(Long userId, Long interviewId) {
        InterviewEntity interview = findInterviewOrThrow(interviewId);
        verifyOwnership(interview, userId);

        if (Boolean.TRUE.equals(interview.getDeleted())) {
            throw new BadRequestException("面试记录已被删除");
        }

        if (interview.getStatus() == InterviewStatusEnum.COMPLETED) {
            interview.setDeleted(true);
            interviewMapper.updateById(interview);

            InterviewMessageEntity msgUpdate = new InterviewMessageEntity();
            msgUpdate.setDeleted(true);
            messageMapper.update(msgUpdate, new LambdaQueryWrapper<InterviewMessageEntity>()
                    .eq(InterviewMessageEntity::getInterviewId, interviewId));
            log.info("面试记录软删除: interviewId={}, userId={}, status={}", interviewId, userId, interview.getStatus());
        } else {
            questionScoreMapper.hardDeleteByInterviewId(interviewId);
            messageMapper.hardDeleteByInterviewId(interviewId);
            interviewMapper.hardDeleteById(interview.getId());
            log.info("面试记录硬删除: interviewId={}, userId={}, status={}", interviewId, userId, interview.getStatus());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SseEmitter controlInterview(Long userId, Long interviewId, ControlCommand command) {
        if (!acquireSsePermit(SSE_PERMIT_KEY)) {
            throw new BadRequestException("服务器连接数已达上限，请稍后再试");
        }
        InterviewEntity interview = findInterviewOrThrow(interviewId);
        verifyOwnership(interview, userId);
        if (interview.getStatus() == InterviewStatusEnum.CREATED) {
            releaseSsePermit(SSE_PERMIT_KEY);
            throw new BadRequestException("面试尚未开始，无法发送控制指令");
        }
        verifyStatusForAnswer(interview);

        if (!acquireSubmitLock(interviewId)) {
            releaseSsePermit(SSE_PERMIT_KEY);
            throw new BadRequestException("请勿频繁提交，请等待上一条处理完成");
        }

        try {
            int nextSeq = getNextSequence(interview.getId());

            ResumeProfile profile = null;
            if (interview.getResumeId() != null) {
                try {
                    ResumeVO resumeVO = resumeClient.getById(interview.getResumeId());
                    profile = ResumeProfileConverter.fromResumeVO(resumeVO);
                } catch (Exception e) {
                    log.warn("加载简历失败: resumeId={}", interview.getResumeId(), e);
                }
            }

            SseEmitter emitter = new SseEmitter(300_000L);
            ResumeProfile finalProfile = profile;
            enqueueAsyncTask(interviewId, () ->
                    processControlAndStream(interview, finalProfile, command, emitter, nextSeq));

            emitter.onCompletion(() -> {
                releaseSsePermit(SSE_PERMIT_KEY);
                dequeueAsyncTask(interviewId);
            });
            emitter.onError(e -> {
                releaseSsePermit(SSE_PERMIT_KEY);
                dequeueAsyncTask(interviewId);
            });
            emitter.onTimeout(() -> {
                releaseSsePermit(SSE_PERMIT_KEY);
                dequeueAsyncTask(interviewId);
            });

            return emitter;
        } finally {
            releaseSubmitLock(interviewId);
        }
    }

    private void processControlAndStream(
            InterviewEntity interview,
            ResumeProfile profile,
            ControlCommand command,
            SseEmitter emitter,
            int nextSeq) {
        final Long currentInterviewId = interview.getId();
        try {
            List<InterviewMessageEntity> history = getMessageHistory(interview.getId());
            InterviewEngine.EngineResult engineResult = interviewEngine.processControlCommand(
                    interview, profile, command.getValue(), history, emitter);

            var aiMessage = new InterviewMessageEntity();
            aiMessage.setInterviewId(interview.getId());
            aiMessage.setRole(MessageRoleEnum.AI);
            aiMessage.setContent(engineResult.aiResponse());
            aiMessage.setQuestionType(engineResult.questionType());
            aiMessage.setSequenceNum(nextSeq);
            aiMessage.setTokenCount(0);
            messageMapper.insert(aiMessage);

            InterviewMessageEntity lastAiMessage = history.stream()
                    .filter(m -> m.getRole() == MessageRoleEnum.AI)
                    .reduce((first, second) -> second)
                    .orElse(aiMessage);
            String lastUserAnswer = history.stream()
                    .filter(m -> m.getRole() == MessageRoleEnum.USER)
                    .reduce((first, second) -> second)
                    .map(InterviewMessageEntity::getContent)
                    .orElse(null);
            interviewExecutor.execute(() -> asyncScoreQuestion(interview.getId(), lastAiMessage, lastUserAnswer));

            interview.setTotalQuestions(interview.getTotalQuestions() + 1);
            if (engineResult.stageCompleted() && engineResult.nextStage() != null) {
                InterviewStageEnum completedStage = interview.getCurrentStage();
                if (engineResult.interviewCompleted()) {
                    interview.setStatus(InterviewStatusEnum.COMPLETED);
                    interview.setCurrentStage(InterviewStageEnum.COMPLETED);
                    interview.setCompletedAt(LocalDateTime.now());
                    if (interview.getStartedAt() != null) {
                        interview.setDurationSeconds((int) ChronoUnit.SECONDS.between(
                                interview.getStartedAt(), interview.getCompletedAt()));
                    }
                    log.info("面试已完成，等待异步评分聚合: interviewId={}", interview.getId());
                } else {
                    interview.setCurrentStage(engineResult.nextStage());
                    interview.setStageOrder(interview.getStageOrder() + 1);
                }
                log.info("阶段流转: interviewId={}, newStage={}, stageOrder={}",
                        interview.getId(), engineResult.nextStage(), interview.getStageOrder());
                scheduleStageAggregation(interview.getId(), completedStage);
            }
            interviewMapper.updateById(interview);

            InterviewVO interviewVO = toInterviewVO(interview);
            InterviewMessageVO aiResponseVO = toMessageVO(aiMessage);

            emitter.send(SseEmitter.event()
                    .name("ai_response")
                    .data(new SubmitAnswerSSEContent(
                            interviewVO, null, aiResponseVO,
                            engineResult.stageCompleted(), engineResult.interviewCompleted(),
                            engineResult.scoreTotal(), engineResult.suggestion())));

            heartbeat(emitter);
            emitter.complete();
            dequeueAsyncTask(currentInterviewId);
        } catch (FeignException e) {
            log.error("远程服务调用失败: interviewId={}, status={}, body={}",
                    currentInterviewId, e.status(), e.contentUTF8(), e);
            completeWithError(emitter, "AI 服务暂时不可用，请稍后重试");
        } catch (Exception e) {
            Throwable root = unwrapRootCause(e);
            if (root instanceof java.io.IOException) {
                log.debug("SSE 连接已断开，中止控制指令处理: interviewId={}", currentInterviewId, e);
            } else {
                log.error("处理控制指令失败: interviewId={}", currentInterviewId, e);
                completeWithError(emitter, "处理失败，请重试");
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public UserPracticeSummaryVO getUserPracticeSummary(Long userId, int recentInterviewLimit, int practicedLimit) {
        List<InterviewEntity> interviews = interviewMapper.selectList(
                new LambdaQueryWrapper<InterviewEntity>()
                        .eq(InterviewEntity::getUserId, userId)
                        .eq(InterviewEntity::getDeleted, false)
                        .eq(InterviewEntity::getStatus, InterviewStatusEnum.COMPLETED)
                        .orderByDesc(InterviewEntity::getCompletedAt)
                        .last("LIMIT " + Math.min(recentInterviewLimit, 50))
        );

        List<UserInterviewScoreVO> recentScores = interviews.stream()
                .map(i -> new UserInterviewScoreVO(
                        i.getId(),
                        i.getCompany(),
                        i.getPosition(),
                        i.getScoreTech(),
                        i.getScoreExpression(),
                        i.getScoreProject(),
                        i.getScoreLogic(),
                        i.getScoreAdaptability(),
                        i.getCompletedAt()
                ))
                .toList();

        List<Long> interviewIds = interviews.stream()
                .map(InterviewEntity::getId)
                .toList();

        List<String> practicedDescriptions = List.of();
        if (!interviewIds.isEmpty()) {
            List<InterviewQuestionScoreEntity> allScores = questionScoreMapper.findByInterviewIds(interviewIds);
            practicedDescriptions = allScores.stream()
                    .map(InterviewQuestionScoreEntity::getQuestionDescription)
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .limit(Math.min(practicedLimit, 200))
                    .toList();
        }

        return new UserPracticeSummaryVO(recentScores, practicedDescriptions);
    }

    // ==================== 内部方法 ====================

    /**
     * 获取请求幂等 ID
     */
    private String getRequestId() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return null;
        return attrs.getRequest().getHeader(ApiConstant.HEADER_REQUEST_ID);
    }

    /**
     * 幂等性检查：防止同一请求重复创建面试
     */
    private void checkDuplicateRequest(String requestId, Long userId) {
        if (requestId == null || requestId.isBlank()) return;
        String key = REQUEST_IDEMPOTENT_PREFIX + requestId;
        Boolean existed = Boolean.TRUE.equals(
                stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.MINUTES));
        if (Boolean.TRUE.equals(existed)) {
            throw new BadRequestException("请求重复，请稍后再试");
        }
    }

    /**
     * SSE 心跳：防止客户端连接超时断开
     */
    private void heartbeat(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().name("heartbeat").data("ping"));
        } catch (Exception e) {
            log.debug("SSE 心跳发送失败", e);
        }
    }

    /**
     * SSE 错误完成
     */
    private void completeWithError(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event().name("error").data(message));
            emitter.complete();
        } catch (IllegalStateException | java.io.IOException ignored) {
            // 流式传输期间已由 DeepSeekClient 完成 emitter，忽略重复完成
        }
    }

    /**
     * 检查用户没有活跃的面试（CREATED 或 IN_PROGRESS）
     */
    private void checkNoActiveInterview(Long userId) {
        Long count = interviewMapper.selectCount(
                new LambdaQueryWrapper<InterviewEntity>()
                        .eq(InterviewEntity::getUserId, userId)
                        .eq(InterviewEntity::getDeleted, false)
                        .in(InterviewEntity::getStatus,
                                InterviewStatusEnum.CREATED, InterviewStatusEnum.IN_PROGRESS));
        if (count != null && count > 0) {
            throw new BadRequestException("您有一个进行中的面试，请先完成或终止该面试");
        }
    }

    /**
     * 查找面试记录，不存在或已删除则抛出异常
     */
    private InterviewEntity findInterviewOrThrow(Long interviewId) {
        InterviewEntity interview = interviewMapper.selectById(interviewId);
        if (interview == null || Boolean.TRUE.equals(interview.getDeleted())) {
            throw new NotFoundException(ErrorCode.INTERVIEW_NOT_FOUND);
        }
        return interview;
    }

    /**
     * 验证面试记录归属权
     */
    private void verifyOwnership(InterviewEntity interview, Long userId) {
        if (!interview.getUserId().equals(userId)) {
            throw new NotFoundException(ErrorCode.INTERVIEW_NOT_FOUND);
        }
    }

    /**
     * 验证面试状态允许提交回答
     */
    private void verifyStatusForAnswer(InterviewEntity interview) {
        if (interview.getStatus() == InterviewStatusEnum.COMPLETED
                || interview.getStatus() == InterviewStatusEnum.TERMINATED
                || interview.getCurrentStage() == InterviewStageEnum.COMPLETED) {
            throw new BadRequestException(ErrorCode.INTERVIEW_ALREADY_COMPLETED);
        }
    }

    /**
     * 获取下一消息序号（仅计算，不插入）
     * 用于控制指令场景：需先获取 AI 响应后才能插入完整消息
     */
    private int getNextSequence(Long interviewId) {
        interviewMapper.selectByIdForUpdate(interviewId);
        Integer maxSeq = messageMapper.selectMaxSeq(interviewId);
        return (maxSeq != null ? maxSeq : 0) + 1;
    }

    /**
     * 获取下一消息序号并插入消息（数据库行锁保证原子性）
     * 锁范围覆盖 selectByIdForUpdate → selectMaxSeq → insert，防止序号竞态
     */
    private record SeqResult(int sequenceNum, Long messageId) {}

    private SeqResult getNextSequenceWithLock(InterviewMessageEntity message) {
        interviewMapper.selectByIdForUpdate(message.getInterviewId());
        Integer maxSeq = messageMapper.selectMaxSeq(message.getInterviewId());
        int nextSeq = (maxSeq != null ? maxSeq : 0) + 1;
        message.setSequenceNum(nextSeq);
        messageMapper.insert(message);
        return new SeqResult(nextSeq, message.getId());
    }

    /**
     * 获取面试消息历史（滑动窗口，最多 20 条）
     */
    private List<InterviewMessageEntity> getMessageHistory(Long interviewId) {
        return messageMapper.findByInterviewId(interviewId, 20);
    }

    // ==================== 异步评分聚合 ====================

    /**
     * 延迟调度阶段评分聚合（给异步题级评分留足完成时间）
     */
    private void scheduleStageAggregation(Long interviewId, InterviewStageEnum stage) {
        interviewScheduler.schedule(() -> aggregateStageScores(interviewId, stage), 10, TimeUnit.SECONDS);
    }

    /**
     * 聚合单个阶段的所有题级评分，写入对应维度；最后一个阶段额外计算总分
     */
    private void aggregateStageScores(Long interviewId, InterviewStageEnum stage) {
        InterviewEntity interview = interviewMapper.selectById(interviewId);
        if (interview == null || Boolean.TRUE.equals(interview.getDeleted())) {
            return;
        }

        QuestionTypeEnum questionType = interviewEngine.getQuestionTypeForStage(stage);
        if (questionType == null) {
            return;
        }

        List<InterviewQuestionScoreEntity> scores = questionScoreMapper.findByInterviewIdAndType(interviewId, questionType);
        if (scores.isEmpty()) {
            log.warn("阶段评分聚合跳过，无题级评分: interviewId={}, stage={}", interviewId, stage);
            return;
        }

        int avgScore = (int) Math.round(scores.stream()
                .mapToInt(InterviewQuestionScoreEntity::getScore)
                .average()
                .orElse(60));

        applyDimensionScore(interview, stage, avgScore);

        if (stage == InterviewStageEnum.REVERSE_QA) {
            interview.setScoreTotal(calculateTotalScore(interview));
        }

        interviewMapper.updateById(interview);
        log.info("阶段评分聚合完成: interviewId={}, stage={}, avgScore={}, scoreTotal={}",
                interviewId, stage, avgScore, interview.getScoreTotal());
    }

    /**
     * 将阶段平均分映射到面试记录的对应维度
     */
    private void applyDimensionScore(InterviewEntity interview, InterviewStageEnum stage, int avgScore) {
        switch (stage) {
            case SELF_INTRO -> interview.setScoreExpression(avgScore);
            case PROJECT_DEEP_DIVE -> interview.setScoreProject(avgScore);
            case TECH_ASSESSMENT -> {
                interview.setScoreTech(avgScore);
                interview.setScoreLogic(avgScore);
            }
            case REVERSE_QA -> interview.setScoreAdaptability(avgScore);
            default -> {}
        }
    }

    /**
     * 根据 5 维度评分计算总分（加权平均）
     */
    private int calculateTotalScore(InterviewEntity interview) {
        int scoreTech = interview.getScoreTech() != null ? interview.getScoreTech() : 60;
        int scoreExpression = interview.getScoreExpression() != null ? interview.getScoreExpression() : 60;
        int scoreProject = interview.getScoreProject() != null ? interview.getScoreProject() : 60;
        int scoreLogic = interview.getScoreLogic() != null ? interview.getScoreLogic() : 60;
        int scoreAdaptability = interview.getScoreAdaptability() != null ? interview.getScoreAdaptability() : 60;

        return Math.min(100, Math.round(0.25f * scoreTech + 0.20f * scoreExpression + 0.20f * scoreProject + 0.20f * scoreLogic + 0.15f * scoreAdaptability));
    }

    // ponytail: taskExecutor = interviewExecutor; no @Async to avoid self-invocation proxy issue
    private void asyncScoreQuestion(Long interviewId, InterviewMessageEntity aiMessage, String candidateAnswer) {
        if (aiMessage.getQuestionType() == null) return;
        try {
            InterviewEngine.QuestionScore qs = interviewEngine.scoreQuestion(
                    aiMessage.getContent(), aiMessage.getQuestionType(), candidateAnswer);
            var entity = new InterviewQuestionScoreEntity();
            entity.setInterviewId(interviewId);
            entity.setMessageId(aiMessage.getId());
            entity.setQuestionType(aiMessage.getQuestionType());
            entity.setQuestionDescription(qs.questionDescription());
            entity.setScore(qs.score());
            entity.setFeedback(qs.feedback());
            questionScoreMapper.insert(entity);
        } catch (Exception e) {
            log.error("题级评分失败: interviewId={}, messageId={}", interviewId, aiMessage.getId(), e);
        }
    }

    // ==================== 幂等性检查 ====================

    /**
     * 后端原子锁：防止短时间内重复提交（防连点/绕过前端攻击）
     * 使用 Redis SET NX EX，5秒内同一面试只能提交一次
     */
    private boolean acquireSubmitLock(Long interviewId) {
        String lockKey = SUBMIT_LOCK_PREFIX + interviewId;
        return Boolean.TRUE.equals(
                stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", SUBMIT_LOCK_TTL_SECONDS, TimeUnit.SECONDS)
        );
    }

    private void releaseSubmitLock(Long interviewId) {
        stringRedisTemplate.delete(SUBMIT_LOCK_PREFIX + interviewId);
    }

    // ==================== VO 转换 ====================

    private InterviewVO toInterviewVO(InterviewEntity entity) {
        // 查询枚举显示名（字符串 → 枚举 → displayName）
        String companyDisplayName = lookupCompanyDisplayName(entity.getCompany());
        String positionDisplayName = lookupPositionDisplayName(entity.getPosition());

        return new InterviewVO(
                entity.getId(),
                entity.getUserId(),
                entity.getCompany(),
                companyDisplayName,
                entity.getPosition(),
                positionDisplayName,
                entity.getStatus(),
                entity.getCurrentStage(),
                entity.getStageOrder(),
                entity.getTotalQuestions(),
                entity.getScoreTotal(),
                entity.getScoreTech(),
                entity.getScoreExpression(),
                entity.getScoreProject(),
                entity.getScoreLogic(),
                entity.getScoreAdaptability(),
                entity.getSuggestion(),
                entity.getStartedAt(),
                entity.getCompletedAt(),
                entity.getDurationSeconds(),
                entity.getCreateTime()
        );
    }

    private InterviewListVO toListVO(InterviewEntity entity) {
        return new InterviewListVO(
                entity.getId(),
                entity.getCompany(),
                entity.getPosition(),
                entity.getStatus(),
                entity.getCurrentStage(),
                entity.getTotalQuestions(),
                entity.getScoreTotal(),
                entity.getStartedAt(),
                entity.getCompletedAt(),
                entity.getDurationSeconds(),
                entity.getCreateTime()
        );
    }

    private InterviewMessageVO toMessageVO(InterviewMessageEntity entity) {
        String content = InterviewEngine.stripRoundCompleteToken(entity.getContent());
        return new InterviewMessageVO(
                entity.getId(),
                entity.getInterviewId(),
                entity.getRole(),
                content,
                entity.getQuestionType(),
                entity.getSequenceNum(),
                entity.getTokenCount(),
                entity.getCreatedAt()
        );
    }

    /**
     * 根据公司字符串查找显示名
     */
    private String lookupCompanyDisplayName(String company) {
        if (company == null || company.isBlank()) {
            return "";
        }
        try {
            return CompanyEnum.valueOf(company).getDisplayName();
        } catch (IllegalArgumentException e) {
            return company;
        }
    }

    /**
     * 根据职位字符串查找显示名
     */
    private String lookupPositionDisplayName(String position) {
        if (position == null || position.isBlank()) {
            return "";
        }
        try {
            return PositionEnum.valueOf(position).getDisplayName();
        } catch (IllegalArgumentException e) {
            return position;
        }
    }
}
