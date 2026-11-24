package com.prepdeck.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 注册请求
 *
 * @param phone    手机号（登录凭证）
 * @param password 密码（明文，6-32 位）
 * @param nickname 用户昵称
 */
public record RegisterRequest(
        @NotBlank(message = "手机号不能为空")
        String phone,

        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 32, message = "密码长度需在 6-32 位之间")
        String password,

        @NotBlank(message = "昵称不能为空")
        String nickname
) {
}
