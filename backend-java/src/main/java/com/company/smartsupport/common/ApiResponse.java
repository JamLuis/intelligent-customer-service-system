package com.company.smartsupport.common;

public record ApiResponse<T>(String code, String message, String requestId, T data) {

    public static <T> ApiResponse<T> success(String requestId, T data) {
        return new ApiResponse<>("0", "success", requestId, data);
    }

    public static <T> ApiResponse<T> failure(String code, String message, String requestId) {
        return new ApiResponse<>(code, message, requestId, null);
    }
}
