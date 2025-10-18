package com.example.codecompare.rebuild.diff.exception;

/**
 * diff 模块统一异常。
 */
public class DiffEngineException extends RuntimeException {

    public DiffEngineException(String message) {
        super(message);
    }

    public DiffEngineException(String message, Throwable cause) {
        super(message, cause);
    }
}
