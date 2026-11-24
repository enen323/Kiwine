package com.prepdeck.common.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Token 黑名单 —— 管理黑名单 key 的生成逻辑。
 *
 * <p>实际的 Redis 操作由各服务自行完成，common 模块不引入 Spring Data Redis 依赖。</p>
 */
public final class TokenBlacklist {

    private static final Logger log = LoggerFactory.getLogger(TokenBlacklist.class);
    private static final String BLACKLIST_PREFIX = "token:blacklist:";

    private TokenBlacklist() {
    }

    public static String blacklistKey(String jti) {
        return BLACKLIST_PREFIX + jti;
    }

    public static String blacklistKeyPrefix() {
        return BLACKLIST_PREFIX;
    }
}
