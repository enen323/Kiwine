package com.prepdeck.user.dto.request;

/**
 * 用户信息更新请求
 *
 * <p>所有字段均为可选，仅更新非 null 值。</p>
 *
 * @param nickname       昵称
 * @param targetPosition 目标岗位
 * @param targetCompany  目标公司
 */
public record UserUpdateRequest(
        String nickname,
        String targetPosition,
        String targetCompany
) {
}
