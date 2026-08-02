package io.regionevent.regioneventbackend.global.error;

import java.util.Arrays;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    CONTENT_STATE_CONFLICT(HttpStatus.CONFLICT, "CONTENT_STATE_CONFLICT", "콘텐츠 상태가 요청을 처리할 수 없습니다."),
    RESERVATION_HOLD_CONFLICT(HttpStatus.CONFLICT, "RESERVATION_HOLD_CONFLICT", "예약 대기를 생성할 수 없는 상태입니다."),
    IDEMPOTENCY_KEY_CONFLICT(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_CONFLICT", "멱등 키가 다른 요청에 이미 사용되었습니다."),
    IDEMPOTENCY_REQUEST_IN_PROGRESS(HttpStatus.CONFLICT, "IDEMPOTENCY_REQUEST_IN_PROGRESS", "동일한 요청이 처리 중입니다."),
    RESERVATION_CONFIRM_CONFLICT(HttpStatus.CONFLICT, "RESERVATION_CONFIRM_CONFLICT", "예약을 확정할 수 없는 상태입니다."),
    REFRESH_TOKEN_CONFLICT(HttpStatus.CONFLICT, "REFRESH_TOKEN_CONFLICT", "토큰 갱신 요청이 이미 진행 중입니다."),
    OPERATOR_APPLICATION_PENDING(HttpStatus.CONFLICT, "OPERATOR_APPLICATION_PENDING", "처리 중인 운영자 권한 신청이 있습니다."),
    OPERATOR_APPLICATION_REAPPLICATION_NOT_ALLOWED(HttpStatus.CONFLICT, "OPERATOR_APPLICATION_REAPPLICATION_NOT_ALLOWED", "운영자 권한 재신청을 할 수 없습니다."),

    INVALID_INPUT(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "요청 값이 올바르지 않습니다."),
    INVALID_JSON(HttpStatus.BAD_REQUEST, "INVALID_JSON", "요청 본문 형식이 올바르지 않습니다."),
    INVALID_TYPE(HttpStatus.BAD_REQUEST, "INVALID_TYPE", "요청 값의 형식이 올바르지 않습니다."),
    DUPLICATE_LOGIN_IDENTIFIER(HttpStatus.CONFLICT, "DUPLICATE_LOGIN_IDENTIFIER", "이미 사용 중인 이메일입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "이메일 또는 비밀번호가 올바르지 않습니다."),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "인증 정보가 없거나 유효하지 않습니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "FORBIDDEN", "접근 권한이 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "NOT_FOUND", "요청한 리소스를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "허용되지 않은 HTTP 메서드입니다."),
    AUTH_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_SERVICE_UNAVAILABLE", "인증 서비스를 일시적으로 사용할 수 없습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "서버 오류가 발생했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }

    public static ErrorCode fromCode(String code) {
        return Arrays.stream(values())
            .filter(errorCode -> errorCode.code.equals(code))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unsupported error code: " + code));
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
    }
}
