package io.regionevent.regioneventbackend.global.response;

import java.util.Objects;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import io.regionevent.regioneventbackend.global.error.ErrorCode;

public record ApiResponse<T>(
    int statusCode,
    String code,
    String message,
    T data
) {

    private static final String SUCCESS_CODE = "SUCCESS";
    private static final String BEARER_PREFIX = "Bearer ";

    public ApiResponse {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(message, "message must not be null");
    }

    public static <T> ApiResponse<T> success(
        HttpStatus statusCode,
        String message,
        T data
    ) {
        return new ApiResponse<>(statusCode.value(), SUCCESS_CODE, message, data);
    }

    public static ApiResponse<Void> success(HttpStatus statusCode, String message) {
        return success(statusCode, message, null);
    }

    public static ApiResponse<Void> fail(ErrorCode errorCode) {
        return new ApiResponse<>(
            errorCode.httpStatus().value(),
            errorCode.code(),
            errorCode.message(),
            null
        );
    }

    public ResponseEntity<ApiResponse<T>> toResponseEntity() {
        return ResponseEntity.status(statusCode).body(this);
    }

    public ResponseEntity<ApiResponse<T>> toResponseEntity(String accessToken) {
        return ResponseEntity
            .status(statusCode)
            .header(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + accessToken)
            .body(this);
    }
}
