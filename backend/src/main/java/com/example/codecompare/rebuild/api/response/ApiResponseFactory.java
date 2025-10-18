package com.example.codecompare.rebuild.api.response;

/**
 * 统一封装响应创建，后续扩展（如 traceId）可以集中处理。
 */
public final class ApiResponseFactory {

    private ApiResponseFactory() {
    }

    public static <T> ApiResponse<T> ok(T data) {
        return ApiResponse.success(data);
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        return ApiResponse.success(message, data);
    }

    public static ApiResponse<Void> okMessage(String message) {
        return ApiResponse.successMessage(message);
    }

    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.failure(message);
    }
}
