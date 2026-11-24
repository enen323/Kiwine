package com.prepdeck.user.dto.response;

import java.time.LocalDateTime;

/**
 * 用户视图对象
 *
 * @param id             用户 ID
 * @param phone          手机号
 * @param nickname       昵称
 * @param avatarUrl      头像 URL
 * @param targetPosition 目标岗位
 * @param targetCompany  目标公司
 * @param createTime     注册时间
 */
public record UserVO(
        Long id,
        String phone,
        String nickname,
        String avatarUrl,
        String targetPosition,
        String targetCompany,
        LocalDateTime createTime
) {
}
