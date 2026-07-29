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

        HttpStatus httpStatus = HttpStatus.valueOf(statusCode);
        if (httpStatus.is2xxSuccessful()) {
            validateSuccessResponse(code);
        } else if (httpStatus.is4xxClientError() || httpStatus.is5xxServerError()) {
            validateErrorResponse(httpStatus, code, message, data);
        } else {
            throw new IllegalArgumentException("Unsupported HTTP status code: " + statusCode);
        }
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

    private static void validateSuccessResponse(String code) {
        if (!SUCCESS_CODE.equals(code)) {
            throw new IllegalArgumentException("Successful responses must use the SUCCESS code");
        }
    }

    private static void validateErrorResponse(
        HttpStatus httpStatus,
        String code,
        String message,
        Object data
    ) {
        ErrorCode errorCode = ErrorCode.fromCode(code);
        if (data != null) {
            throw new IllegalArgumentException("Error responses must not contain data");
        }
        if (errorCode.httpStatus() != httpStatus || !errorCode.message().equals(message)) {
            throw new IllegalArgumentException("Error response fields must match the error code");
        }
    }
}
