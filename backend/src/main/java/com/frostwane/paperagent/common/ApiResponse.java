package com.frostwane.paperagent.common;

public record ApiResponse<T>(boolean ok, T data, String message) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, "");
    }

    public static ApiResponse<Void> empty() {
        return new ApiResponse<>(true, null, "");
    }

    public static ApiResponse<Void> fail(String message) {
        return new ApiResponse<>(false, null, message);
    }
}
