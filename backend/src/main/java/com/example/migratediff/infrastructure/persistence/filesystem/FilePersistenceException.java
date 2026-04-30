package com.example.migratediff.infrastructure.persistence.filesystem;

/**
 * 文件持久化统一异常，包装底层 IO 或序列化错误。
 */
public class FilePersistenceException extends RuntimeException {

    public FilePersistenceException(String message, Throwable cause) {
        super(message, cause);
    }

    public FilePersistenceException(String message) {
        super(message);
    }
}
