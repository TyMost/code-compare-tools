package com.example.codecompare.rebuild.core.config;

import com.example.codecompare.rebuild.core.logging.StructuredLoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 结构化日志配置，基于 ObjectMapper 输出 JSON 上下文。
 */
@Configuration(proxyBeanMethods = false)
public class StructuredLoggerConfiguration {

    @Bean
    public StructuredLoggerFactory structuredLoggerFactory(ObjectMapper objectMapper) {
        return new StructuredLoggerFactory(objectMapper);
    }
}
