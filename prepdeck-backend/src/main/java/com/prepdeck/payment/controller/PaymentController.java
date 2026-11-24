package com.prepdeck.payment.controller;

import com.prepdeck.common.model.Result;
import com.prepdeck.payment.dto.request.ChargeRequest;
import com.prepdeck.payment.dto.response.ChargeResponse;
import com.prepdeck.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/charge")
    public Result<ChargeResponse> charge(@Valid @RequestBody ChargeRequest request) {
        return Result.success(paymentService.charge(request));
    }

    @PostMapping(value = "/callback/wechat", consumes = "application/xml")
    public String wechatCallback(@RequestBody String xmlBody) {
        paymentService.handleWechatCallback(xmlBody);
        return "<xml><return_code><![CDATA[SUCCESS]]></return_code><return_msg><![CDATA[OK]]></return_msg></xml>";
    }
}
