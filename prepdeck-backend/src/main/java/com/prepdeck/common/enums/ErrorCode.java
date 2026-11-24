package com.prepdeck.common.enums;

/**
 * 错误码枚举
 *
 * <p>错误码分段：<br>
 * 0      — 成功<br>
 * 1xxxx  — 通用参数错误<br>
 * 2xxxx  — 认证鉴权<br>
 * 3xxxx  — 用户<br>
 * 4xxxx  — 简历<br>
 * 5xxxx  — 面试<br>
 * 6xxxx  — 报告<br>
 * 9xxxx  — 系统
 */
public enum ErrorCode {

    // ========== 成功 ==========
    SUCCESS(0, "success"),

    // ========== 通用：1xxxx ==========
    BAD_REQUEST(10000, "请求参数错误"),
    MISSING_PARAM(10001, "缺少必要参数"),
    PARAM_INVALID(10002, "参数校验不通过"),

    // ========== 认证：2xxxx ==========
    UNAUTHORIZED(20000, "未登录"),
    TOKEN_EXPIRED(20001, "Token 已过期"),
    TOKEN_INVALID(20002, "Token 无效"),
    FORBIDDEN(20003, "无权限访问"),
    ACCOUNT_DISABLED(20004, "账号已被禁用"),

    // ========== 用户：3xxxx ==========
    USER_NOT_FOUND(30000, "用户不存在"),
    USER_EXISTS(30001, "用户已存在"),
    PASSWORD_ERROR(30002, "密码错误"),

    // ========== 简历：4xxxx ==========
    RESUME_NOT_FOUND(40000, "简历不存在"),
    RESUME_PARSE_FAILED(40001, "简历解析失败"),
    RESUME_TOO_LARGE(40002, "简历文件过大"),

    // ========== 面试：5xxxx ==========
    INTERVIEW_NOT_FOUND(50000, "面试记录不存在"),
    INTERVIEW_ALREADY_COMPLETED(50001, "面试已结束"),
    INTERVIEW_INVALID_STATE(50002, "当前面试状态不允许此操作"),
    LLM_SERVICE_ERROR(50003, "AI 服务异常，请稍后重试"),

    // ========== 报告：6xxxx ==========
    REPORT_NOT_FOUND(60000, "报告不存在"),
    REPORT_NOT_READY(60001, "报告生成中"),

    // ========== 系统：9xxxx ==========
    INTERNAL_ERROR(90000, "服务器内部错误"),
    SERVICE_UNAVAILABLE(90001, "服务暂不可用"),
    RATE_LIMITED(90002, "请求过于频繁，请稍后重试"),

    // ========== 商业配额：7xxxx ==========
    QUOTA_EXHAUSTED(70000, "本月免费额度已用完"),
    QUOTA_OPERATION_FAILED(70003, "配额操作失败，请重试"),
    PAYMENT_REQUIRED(70001, "需要付费解锁"),
    PAYMENT_INVALID(70002, "支付凭证无效或已过期"),

    // ========== 题库：8xxxx ==========
    QUESTION_NOT_FOUND(80000, "题目不存在"),
    QUESTION_ALREADY_FAVORITED(80001, "已收藏该题目"),
    QUESTION_NOT_FAVORITED(80002, "未收藏该题目"),
    TAG_NOT_FOUND(80003, "标签不存在"),
    TAG_ALREADY_EXISTS(80004, "标签已存在"),
    DAILY_CHALLENGE_NOT_FOUND(80005, "今日一题不存在"),
    RECOMMENDATION_NOT_ENOUGH_DATA(80006, "暂无足够面试数据，已为你推荐热门题目");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
