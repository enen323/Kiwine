package com.prepdeck.quota.dto.response;

public record QuotaVO(
        Long userId,
        Integer interviewFreeUsed,
        Integer resumeChatFreeUsed,
        Integer interviewPaid,
        Integer resumeChatPaid
) {
}
