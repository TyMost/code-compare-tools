package com.example.migratediff.infrastructure.persistence.filesystem;

import org.springframework.util.StringUtils;

/**
 * 标识符工具类，负责常用的非空校验。
 */
public final class IdentifierUtils {

    private IdentifierUtils() {
    }

    public static String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new MissingIdentifierException(message);
        }
        return value;
    }
}
