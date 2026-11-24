package com.prepdeck.payment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.prepdeck.payment.client.QuotaClient;
import com.prepdeck.payment.dto.request.ChargeRequest;
import com.prepdeck.payment.dto.response.ChargeResponse;
import com.prepdeck.payment.entity.PaymentRecordEntity;
import com.prepdeck.payment.mapper.PaymentRecordMapper;
import com.prepdeck.payment.service.PaymentService;
import com.prepdeck.payment.util.WechatPaySigner;
import com.prepdeck.quota.util.QuotaConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRecordMapper paymentRecordMapper;
    private final QuotaClient quotaClient;
    private final WechatPaySigner wechatPaySigner;

    @Value("${wechatpay.app-id:}")
    private String appId;

    private static final String STATUS_NEED_RETRY = "NEED_RETRY";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_PENDING = "PENDING";

    @Override
    @Transactional
    public ChargeResponse charge(ChargeRequest request) {
        BigDecimal amount = getPrice(request.serviceType());
        String outTradeNo = generateOutTradeNo();

        PaymentRecordEntity record = new PaymentRecordEntity();
        record.setUserId(request.userId());
        record.setAmount(amount);
        record.setServiceType(request.serviceType());
        record.setStatus(STATUS_PENDING);
        record.setWechatTradeNo(outTradeNo);
        paymentRecordMapper.insert(record);

        return new ChargeResponse(
                appId,
                String.valueOf(System.currentTimeMillis() / 1000),
                UUID.randomUUID().toString().substring(0, 32),
                "prepay_id=" + outTradeNo,
                "RSA",
                wechatPaySigner.sign(appId, outTradeNo),
                amount,
                request.serviceType()
        );
    }

    @Override
    public void handleWechatCallback(String xmlBody) {
        if (!wechatPaySigner.verify(xmlBody)) {
            log.warn("WeChat callback signature verification failed");
            return;
        }

        String outTradeNo = wechatPaySigner.extractOutTradeNo(xmlBody);
        PaymentRecordEntity record = paymentRecordMapper.selectOne(
                new LambdaQueryWrapper<PaymentRecordEntity>()
                        .eq(PaymentRecordEntity::getWechatTradeNo, outTradeNo)
        );
        if (record == null || STATUS_SUCCESS.equals(record.getStatus())) {
            return;
        }

        record.setStatus(STATUS_SUCCESS);
        paymentRecordMapper.updateById(record);

        try {
            quotaClient.grantPaid(record.getUserId(), record.getServiceType());
            log.info("Payment success: userId={}, serviceType={}, amount={}, tradeNo={}",
                    record.getUserId(), record.getServiceType(), record.getAmount(), outTradeNo);
        } catch (Exception e) {
            log.error("quota grantPaid failed after payment: userId={}, tradeNo={}",
                    record.getUserId(), outTradeNo, e);
            record.setStatus(STATUS_NEED_RETRY);
            paymentRecordMapper.updateById(record);
        }
    }

    private String generateOutTradeNo() {
        return "MB" + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + UUID.randomUUID().toString().substring(0, 8);
    }

    private BigDecimal getPrice(String serviceType) {
        return switch (serviceType) {
            case "INTERVIEW" -> QuotaConstants.INTERVIEW_PRICE;
            case "RESUME_CHAT" -> QuotaConstants.RESUME_CHAT_PRICE;
            default -> throw new IllegalArgumentException("Unknown serviceType: " + serviceType);
        };
    }
}
