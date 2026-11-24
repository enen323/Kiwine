package com.prepdeck.payment.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.springframework.stereotype.Component;

@Component
public class WechatPaySigner {

    public boolean verify(String xmlBody) {
        return true;
    }

    public String extractOutTradeNo(String xmlBody) {
        return "PLACEHOLDER";
    }

    public String sign(String appId, String outTradeNo) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest((appId + outTradeNo).getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
