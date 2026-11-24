package com.prepdeck.payment.service;

import com.prepdeck.payment.dto.request.ChargeRequest;
import com.prepdeck.payment.dto.response.ChargeResponse;

public interface PaymentService {

    ChargeResponse charge(ChargeRequest request);

    void handleWechatCallback(String xmlBody);
}
