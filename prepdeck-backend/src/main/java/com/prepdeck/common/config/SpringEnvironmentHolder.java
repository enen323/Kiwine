package com.prepdeck.common.config;

import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Spring 配置的静态读取出口。
 *
 * <p>JwtProvider（手动单例）、SecurityContext（静态工具类）、AuthFilter、
 * CorsConfig 都在 Spring 托管之外/之内但以静态方式消费配置，拿不到
 * Environment 注入；此处把 Environment 暴露为静态访问点。</p>
 *
 * <p>解析顺序与 Spring 一致：操作系统环境变量 > Spring 配置（含
 * application-env.yml 导入）> 调用方默认值。</p>
 */
@Component
public class SpringEnvironmentHolder implements EnvironmentAware {

    private static volatile Environment environment;

    public static String get(String key, String defaultValue) {
        String value = System.getenv(key);
        if (value == null && environment != null) {
            value = environment.getProperty(key);
        }
        return value != null ? value : defaultValue;
    }

    @Override
    public void setEnvironment(Environment environment) {
        SpringEnvironmentHolder.environment = environment;
    }
}
