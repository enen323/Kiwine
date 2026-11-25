package com.prepdeck.quota.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.prepdeck.common.exception.BadRequestException;
import com.prepdeck.common.util.SecurityContext;
import com.prepdeck.quota.dto.response.QuotaCheckResult;
import com.prepdeck.quota.dto.response.QuotaVO;
import com.prepdeck.quota.domain.UserQuotaEntity;
import com.prepdeck.quota.repository.UserQuotaMapper;
import com.prepdeck.quota.util.QuotaConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuotaServiceImpl extends ServiceImpl<UserQuotaMapper, UserQuotaEntity> implements QuotaService {

    private final UserQuotaMapper userQuotaMapper;
    private final StringRedisTemplate stringRedisTemplate;

    private static final String MONTHLY_RESET_LOCK_KEY = "quota:reset:lock";
    private static final long MONTHLY_RESET_LOCK_TTL = TimeUnit.HOURS.toSeconds(1);

    // REQUIRES_NEW：原跨服务调用经 HTTP 边界各自提交（先提交后返回）；单体化后若沿用默认
    // REQUIRED，配额写会并入调用方事务、随调用方回滚，语义被改变。故配额侧写方法一律挂起
    // 调用方事务、独立提交。只读的 getQuota 不加。
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public QuotaCheckResult consume(String serviceType) {
        Long userId = SecurityContext.getCurrentUserId();
        return doConsume(userId, serviceType);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deductFree(String serviceType) {
        Long userId = SecurityContext.getCurrentUserId();
        doDeductFree(userId, serviceType);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deductPaid(String serviceType) {
        Long userId = SecurityContext.getCurrentUserId();
        doDeductPaid(userId, serviceType);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void grantPaid(String serviceType) {
        Long userId = SecurityContext.getCurrentUserId();
        doGrantPaid(userId, serviceType);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void grantPaid(Long userId, String serviceType) {
        doGrantPaid(userId, serviceType);
    }

    @Override
    @Transactional
    public QuotaVO getQuota() {
        Long userId = SecurityContext.getCurrentUserId();
        UserQuotaEntity quota = getOrCreate(userId);
        return new QuotaVO(
                quota.getUserId(),
                quota.getInterviewFreeUsed(),
                quota.getResumeChatFreeUsed(),
                quota.getInterviewPaid(),
                quota.getResumeChatPaid()
        );
    }

    @Override
    @Scheduled(cron = "0 0 0 1 * *")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void resetMonthly() {
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(
                MONTHLY_RESET_LOCK_KEY, "1", MONTHLY_RESET_LOCK_TTL, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(locked)) {
            log.info("Monthly quota reset skipped: another instance is handling it");
            return;
        }
        try {
            userQuotaMapper.resetInterviewFreeUsed();
            userQuotaMapper.resetResumeChatFreeUsed();
            log.info("Monthly quota reset completed at {}", LocalDateTime.now());
        } finally {
            stringRedisTemplate.delete(MONTHLY_RESET_LOCK_KEY);
        }
    }

    private QuotaCheckResult doConsume(Long userId, String serviceType) {
        getOrCreate(userId);
        int rows = "INTERVIEW".equals(serviceType)
                ? userQuotaMapper.tryConsumeInterviewFree(userId, QuotaConstants.FREE_LIMIT)
                : userQuotaMapper.tryConsumeResumeChatFree(userId, QuotaConstants.FREE_LIMIT);
        if (rows > 0) {
            return QuotaCheckResult.allowed("FREE");
        }
        rows = "INTERVIEW".equals(serviceType)
                ? userQuotaMapper.tryConsumeInterviewPaid(userId)
                : userQuotaMapper.tryConsumeResumeChatPaid(userId);
        if (rows > 0) {
            return QuotaCheckResult.allowed("PAID");
        }
        return QuotaCheckResult.denied("QUOTA_EXHAUSTED", getPrice(serviceType));
    }

    private void doDeductFree(Long userId, String serviceType) {
        if ("INTERVIEW".equals(serviceType)) {
            userQuotaMapper.incrementInterviewFreeUsed(userId);
        } else if ("RESUME_CHAT".equals(serviceType)) {
            userQuotaMapper.incrementResumeChatFreeUsed(userId);
        }
    }

    private void doDeductPaid(Long userId, String serviceType) {
        if ("INTERVIEW".equals(serviceType)) {
            userQuotaMapper.decrementInterviewPaid(userId);
        } else if ("RESUME_CHAT".equals(serviceType)) {
            userQuotaMapper.decrementResumeChatPaid(userId);
        }
    }

    private void doGrantPaid(Long userId, String serviceType) {
        if ("INTERVIEW".equals(serviceType)) {
            userQuotaMapper.incrementInterviewPaid(userId);
        } else if ("RESUME_CHAT".equals(serviceType)) {
            userQuotaMapper.incrementResumeChatPaid(userId);
        }
    }

    private UserQuotaEntity getOrCreate(Long userId) {
        UserQuotaEntity quota = getById(userId);
        if (quota == null) {
            userQuotaMapper.insertIgnore(userId);
            quota = getById(userId);
            if (quota == null) {
                quota = new UserQuotaEntity();
                quota.setUserId(userId);
                quota.setInterviewFreeUsed(0);
                quota.setResumeChatFreeUsed(0);
                quota.setInterviewPaid(0);
                quota.setResumeChatPaid(0);
                save(quota);
            }
        }
        return quota;
    }

    private int getFreeUsed(UserQuotaEntity quota, String serviceType) {
        return switch (serviceType) {
            case "INTERVIEW" -> quota.getInterviewFreeUsed();
            case "RESUME_CHAT" -> quota.getResumeChatFreeUsed();
            default -> throw new BadRequestException("Unknown serviceType: " + serviceType);
        };
    }

    private int getPaid(UserQuotaEntity quota, String serviceType) {
        return switch (serviceType) {
            case "INTERVIEW" -> quota.getInterviewPaid();
            case "RESUME_CHAT" -> quota.getResumeChatPaid();
            default -> throw new BadRequestException("Unknown serviceType: " + serviceType);
        };
    }

    private BigDecimal getPrice(String serviceType) {
        return switch (serviceType) {
            case "INTERVIEW" -> QuotaConstants.INTERVIEW_PRICE;
            case "RESUME_CHAT" -> QuotaConstants.RESUME_CHAT_PRICE;
            default -> throw new BadRequestException("Unknown serviceType: " + serviceType);
        };
    }
}
