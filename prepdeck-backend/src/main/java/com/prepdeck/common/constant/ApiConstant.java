package com.prepdeck.common.constant;

/**
 * API 相关常量
 */
public final class ApiConstant {

    private ApiConstant() {
    }

    /** API 版本前缀 */
    public static final String API_V1 = "/api/v1";

    /** 分页默认值 */
    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final int MAX_PAGE_SIZE = 100;

    /** 请求头 */
    public static final String HEADER_TRACE_ID = "X-Trace-Id";
    public static final String HEADER_REQUEST_ID = "X-Request-Id";
}
