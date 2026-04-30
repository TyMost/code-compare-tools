package com.example.migratediff.infrastructure.persistence.filesystem;

/**
 * 当领域对象缺少必要标识时抛出，提醒业务层补齐。
 */
public class MissingIdentifierException extends RuntimeException {

    public MissingIdentifierException(String message) {
        super(message);
    }
}
