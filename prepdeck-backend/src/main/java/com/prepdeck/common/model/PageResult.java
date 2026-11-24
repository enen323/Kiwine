package com.prepdeck.common.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 分页响应体
 *
 * @param <T> data 元素类型
 */
public record PageResult<T>(
        @JsonProperty("code") int code,
        @JsonProperty("message") String message,
        @JsonProperty("data") List<T> data,
        @JsonProperty("page") int page,
        @JsonProperty("pageSize") int pageSize,
        @JsonProperty("total") long total
) {

    public static <T> PageResult<T> success(List<T> data, int page, int pageSize, long total) {
        return new PageResult<>(0, "success", data, page, pageSize, total);
    }
}
