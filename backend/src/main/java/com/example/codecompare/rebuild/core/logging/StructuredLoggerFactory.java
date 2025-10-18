package com.example.codecompare.rebuild.core.logging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 简易结构化日志工厂，可在保留 SLF4J 模板的同时输出 JSON 上下文。
 */
public class StructuredLoggerFactory {

    private final ObjectMapper objectMapper;

    public StructuredLoggerFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 基于类获取结构化日志对象。
     */
    public StructuredLogger getLogger(Class<?> type) {
        return new StructuredLogger(LoggerFactory.getLogger(type), objectMapper);
    }

    /**
     * 自定义名称创建结构化日志对象。
     */
    public StructuredLogger getLogger(String name) {
        return new StructuredLogger(LoggerFactory.getLogger(name), objectMapper);
    }

    /**
     * 结构化日志包装器，提供简洁的上下文输出能力。
     */
    public static class StructuredLogger {

        private final Logger delegate;
        private final ObjectMapper objectMapper;

        StructuredLogger(Logger delegate, ObjectMapper objectMapper) {
            this.delegate = delegate;
            this.objectMapper = objectMapper;
        }

        public void info(String message, Map<String, Object> fields) {
            if (delegate.isInfoEnabled()) {
                delegate.info("{} | {}", message, toJson(fields));
            }
        }

        public void warn(String message, Map<String, Object> fields) {
            if (delegate.isWarnEnabled()) {
                delegate.warn("{} | {}", message, toJson(fields));
            }
        }

        public void error(String message, Throwable throwable, Map<String, Object> fields) {
            delegate.error("{} | {}", message, toJson(fields), throwable);
        }

        private String toJson(Map<String, Object> fields) {
            Map<String, Object> safeFields = fields == null ? Collections.emptyMap() : new LinkedHashMap<>(fields);
            try {
                if (safeFields.isEmpty()) {
                    return "{}";
                }
                return objectMapper.writeValueAsString(safeFields);
            } catch (JsonProcessingException ex) {
                return "{\"serializeError\":\"" + ex.getMessage() + "\"}";
            }
        }
    }
}
