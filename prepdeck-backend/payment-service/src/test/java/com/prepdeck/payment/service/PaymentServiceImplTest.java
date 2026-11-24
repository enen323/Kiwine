package com.prepdeck.payment.service;

import com.prepdeck.payment.client.QuotaClient;
import com.prepdeck.payment.dto.request.ChargeRequest;
import com.prepdeck.payment.dto.response.ChargeResponse;
import com.prepdeck.payment.entity.PaymentRecordEntity;
import com.prepdeck.payment.mapper.PaymentRecordMapper;
import com.prepdeck.payment.service.impl.PaymentServiceImpl;
import com.prepdeck.payment.util.WechatPaySigner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private PaymentRecordMapper paymentRecordMapper;

    @Mock
    private QuotaClient quotaClient;

    @Mock
    private WechatPaySigner wechatPaySigner;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    @Test
    void charge_createsPendingRecord() {
        ChargeRequest request = new ChargeRequest(1L, "INTERVIEW");
        given(wechatPaySigner.sign(any(), anyString())).willReturn("mock-signature");
        given(paymentRecordMapper.insert(any(PaymentRecordEntity.class))).willReturn(1);

        ChargeResponse response = paymentService.charge(request);

        assertNotNull(response);
        assertEquals(new BigDecimal("1.99"), response.amount());
        assertEquals("INTERVIEW", response.serviceType());
        assertEquals("mock-signature", response.paySign());

        ArgumentCaptor<PaymentRecordEntity> captor = ArgumentCaptor.forClass(PaymentRecordEntity.class);
        verify(paymentRecordMapper).insert(captor.capture());
        PaymentRecordEntity saved = captor.getValue();
        assertEquals(1L, saved.getUserId());
        assertEquals("INTERVIEW", saved.getServiceType());
        assertEquals("PENDING", saved.getStatus());
        assertNotNull(saved.getWechatTradeNo());
        assertTrue(saved.getWechatTradeNo().startsWith("MB"));
    }

    @Test
    void handleWechatCallback_grantsPaidQuotaOnSuccess() {
        String xmlBody = "<xml>...</xml>";
        PaymentRecordEntity record = new PaymentRecordEntity();
        record.setId(100L);
        record.setUserId(1L);
        record.setServiceType("INTERVIEW");
        record.setStatus("PENDING");
        record.setWechatTradeNo("WX123");

        given(wechatPaySigner.verify(xmlBody)).willReturn(true);
        given(wechatPaySigner.extractOutTradeNo(xmlBody)).willReturn("WX123");
        given(paymentRecordMapper.selectOne(any())).willReturn(record);
        doReturn(1).when(paymentRecordMapper).updateById(record);

        paymentService.handleWechatCallback(xmlBody);

        assertEquals("SUCCESS", record.getStatus());
        verify(paymentRecordMapper, times(1)).updateById(record);
        verify(quotaClient).grantPaid(1L, "INTERVIEW");
    }

    // 测：Feign 调用 grantPaid 失败 → 记录标记为 NEED_RETRY，不抛异常（P0-1 修复）
    @Test
    void handleWechatCallback_marksNeedRetryWhenGrantPaidFails() {
        String xmlBody = "<xml>...</xml>";
        PaymentRecordEntity record = new PaymentRecordEntity();
        record.setId(100L);
        record.setUserId(1L);
        record.setServiceType("INTERVIEW");
        record.setStatus("PENDING");
        record.setWechatTradeNo("WX123");

        given(wechatPaySigner.verify(xmlBody)).willReturn(true);
        given(wechatPaySigner.extractOutTradeNo(xmlBody)).willReturn("WX123");
        given(paymentRecordMapper.selectOne(any())).willReturn(record);
        doReturn(1).when(paymentRecordMapper).updateById(record);
        doThrow(new RuntimeException("Feign 500")).when(quotaClient).grantPaid(anyLong(), anyString());

        paymentService.handleWechatCallback(xmlBody);

        assertEquals("NEED_RETRY", record.getStatus());
        verify(paymentRecordMapper, times(2)).updateById(record);
        verify(quotaClient).grantPaid(1L, "INTERVIEW");
    }

    // 测：已成功回调的记录再次到达 → 跳过，不重复处理
    @Test
    void handleWechatCallback_returnsEarlyWhenRecordAlreadySuccess() {
        String xmlBody = "<xml>...</xml>";
        PaymentRecordEntity record = new PaymentRecordEntity();
        record.setStatus("SUCCESS");

        given(wechatPaySigner.verify(xmlBody)).willReturn(true);
        given(wechatPaySigner.extractOutTradeNo(xmlBody)).willReturn("WX123");
        given(paymentRecordMapper.selectOne(any())).willReturn(record);

        paymentService.handleWechatCallback(xmlBody);

        verify(paymentRecordMapper, never()).updateById(any(PaymentRecordEntity.class));
        verify(quotaClient, never()).grantPaid(any(), any());
    }

    @Test
    void charge_resumeChat_usesCorrectPrice() {
        ChargeRequest request = new ChargeRequest(2L, "RESUME_CHAT");
        given(wechatPaySigner.sign(any(), anyString())).willReturn("mock-signature");
        given(paymentRecordMapper.insert(any(PaymentRecordEntity.class))).willReturn(1);

        ChargeResponse response = paymentService.charge(request);

        assertEquals(new BigDecimal("0.99"), response.amount());
        assertEquals("RESUME_CHAT", response.serviceType());
    }
}
