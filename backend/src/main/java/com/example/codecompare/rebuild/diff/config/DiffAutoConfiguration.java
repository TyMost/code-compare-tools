package com.example.codecompare.rebuild.diff.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * diff 模块自动配置。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DiffConfigurationProperties.class)
public class DiffAutoConfiguration {
}
