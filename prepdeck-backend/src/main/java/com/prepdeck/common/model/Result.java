package com.prepdeck.common.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.prepdeck.common.enums.ErrorCode;

/**
 * 统一响应体
 *
 * <p>所有 API 接口必须使用此类包装返回。</p>
 *
 * @param <T> data 类型
 */
public record Result<T>(
        @JsonProperty("code") int code,
        @JsonProperty("message") String message,
        @JsonProperty("data") T data
) {

    public static <T> Result<T> success(T data) {
        return new Result<>(0, "success", data);
    }

    public static <T> Result<T> success() {
        return new Result<>(0, "success", null);
    }

    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null);
    }

    public static <T> Result<T> error(ErrorCode errorCode) {
        return new Result<>(errorCode.getCode(), errorCode.getMessage(), null);
    }
}
