package com.prepdeck.payment.config;

import feign.RequestInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Feign 请求拦截器 —— 为跨服务调用注入来源校验头与用户信息头
 *
 * <p>下游服务（如 quota-service）通过 SecurityContext 校验 X-Gateway-Source
 * 和 X-Gateway-Secret，Feign 调用若缺少这些头会被拒绝（"非法请求来源"）。
 * 此拦截器从当前请求中透传网关注入的鉴权头。</p>
 */
@Configuration
public class FeignHeaderConfig {

    private static final String GATEWAY_SOURCE_HEADER = "X-Gateway-Source";
    private static final String GATEWAY_SOURCE_VALUE = "prepdeck-gateway";
    private static final String GATEWAY_SECRET_HEADER = "X-Gateway-Secret";
    private static final String GATEWAY_SECRET =
            System.getenv().getOrDefault("GATEWAY_SECRET", "10b8e581a75345c78bdc195188a74e0f3dccf51ef33d4452b34c6bed67cd7422");
    private static final String USER_ID_HEADER = "X-User-Id";

    @Bean
    public RequestInterceptor feignHeaderInterceptor() {
        return requestTemplate -> {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                // 透传或回退：请求头存在则透传，不存在（如微信回调）则注入回退值
                String source = request.getHeader(GATEWAY_SOURCE_HEADER);
                requestTemplate.header(GATEWAY_SOURCE_HEADER, source != null ? source : GATEWAY_SOURCE_VALUE);
                String secret = request.getHeader(GATEWAY_SECRET_HEADER);
                requestTemplate.header(GATEWAY_SECRET_HEADER, secret != null ? secret : GATEWAY_SECRET);
                String userId = request.getHeader(USER_ID_HEADER);
                if (userId != null) {
                    requestTemplate.header(USER_ID_HEADER, userId);
                }
            } else {
                // 异步线程 fallback：注入网关来源头，但 X-User-Id 需由调用方设置
                requestTemplate.header(GATEWAY_SOURCE_HEADER, GATEWAY_SOURCE_VALUE);
                requestTemplate.header(GATEWAY_SECRET_HEADER, GATEWAY_SECRET);
            }
        };
    }
}
