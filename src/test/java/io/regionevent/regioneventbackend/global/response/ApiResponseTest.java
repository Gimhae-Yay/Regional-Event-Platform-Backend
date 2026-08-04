package io.regionevent.regioneventbackend.global.response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.provider.Arguments;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import io.regionevent.regioneventbackend.global.error.ErrorCode;

class ApiResponseTest {

    @Test
    void API_응답_계약을_보존한다() {
        assertAll(
            () -> new ApiResponseTest().success_withData_createsSuccessResponse(),
            () -> new ApiResponseTest().success_withoutData_setsDataToNull(),
            () -> new ApiResponseTest().fail_withErrorCode_preservesPublicErrorContract(),
            () -> new ApiResponseTest().toResponseEntity_withAccessToken_addsAuthorizationHeader(),
            () -> new ApiResponseTest().constructor_whenSuccessResponseUsesErrorCode_throwsException(),
            () -> new ApiResponseTest().constructor_whenErrorResponseContainsData_throwsException(),
            () -> new ApiResponseTest().constructor_whenErrorResponseDoesNotMatchErrorCode_throwsException()
        );
    }

    void success_withData_createsSuccessResponse() {
        ApiResponse<String> response = ApiResponse.success(HttpStatus.OK, "조회에 성공했습니다.", "result");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.code()).isEqualTo("SUCCESS");
        assertThat(response.message()).isEqualTo("조회에 성공했습니다.");
        assertThat(response.data()).isEqualTo("result");
    }

    void success_withoutData_setsDataToNull() {
        ApiResponse<Void> response = ApiResponse.success(HttpStatus.CREATED, "등록에 성공했습니다.");

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.code()).isEqualTo("SUCCESS");
        assertThat(response.data()).isNull();
    }

    void fail_withErrorCode_preservesPublicErrorContract() {
        Stream<Executable> contracts = errorCodeContracts().map(arguments -> () -> {
            Object[] values = arguments.get();
            ErrorCode errorCode = (ErrorCode) values[0];
            HttpStatus httpStatus = (HttpStatus) values[1];
            String code = (String) values[2];
            String message = (String) values[3];
            ApiResponse<Void> response = ApiResponse.fail(errorCode);

            assertThat(response.statusCode()).isEqualTo(httpStatus.value());
            assertThat(response.code()).isEqualTo(code);
            assertThat(response.message()).isEqualTo(message);
            assertThat(response.data()).isNull();
        });

        assertAll(contracts);
    }

    void toResponseEntity_withAccessToken_addsAuthorizationHeader() {
        ApiResponse<String> response = ApiResponse.success(HttpStatus.OK, "로그인에 성공했습니다.", "profile");

        var responseEntity = response.toResponseEntity("access-token");

        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(responseEntity.getHeaders().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer access-token");
        assertThat(responseEntity.getBody()).isEqualTo(response);
    }

    void constructor_whenSuccessResponseUsesErrorCode_throwsException() {
        assertThatThrownBy(() -> new ApiResponse<>(
            HttpStatus.OK.value(),
            ErrorCode.FORBIDDEN.code(),
            ErrorCode.FORBIDDEN.message(),
            "result"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    void constructor_whenErrorResponseContainsData_throwsException() {
        assertThatThrownBy(() -> new ApiResponse<>(
            ErrorCode.FORBIDDEN.httpStatus().value(),
            ErrorCode.FORBIDDEN.code(),
            ErrorCode.FORBIDDEN.message(),
            "result"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    void constructor_whenErrorResponseDoesNotMatchErrorCode_throwsException() {
        assertThatThrownBy(() -> new ApiResponse<>(
            ErrorCode.FORBIDDEN.httpStatus().value(),
            ErrorCode.FORBIDDEN.code(),
            "다른 메시지",
            null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private static Stream<Arguments> errorCodeContracts() {
        return Stream.of(
            Arguments.of(
                ErrorCode.CONTENT_STATE_CONFLICT,
                HttpStatus.CONFLICT,
                "CONTENT_STATE_CONFLICT",
                "콘텐츠 상태가 요청을 처리할 수 없습니다."
            ),
            Arguments.of(
                ErrorCode.RESERVATION_HOLD_CONFLICT,
                HttpStatus.CONFLICT,
                "RESERVATION_HOLD_CONFLICT",
                "예약 대기를 생성할 수 없는 상태입니다."
            ),
            Arguments.of(
                ErrorCode.OPERATOR_APPLICATION_PENDING,
                HttpStatus.CONFLICT,
                "OPERATOR_APPLICATION_PENDING",
                "처리 중인 운영자 권한 신청이 있습니다."
            ),
            Arguments.of(
                ErrorCode.OPERATOR_APPLICATION_REAPPLICATION_NOT_ALLOWED,
                HttpStatus.CONFLICT,
                "OPERATOR_APPLICATION_REAPPLICATION_NOT_ALLOWED",
                "운영자 권한 재신청을 할 수 없습니다."
            ),
            Arguments.of(
                ErrorCode.OPERATOR_APPLICATION_STATE_CONFLICT,
                HttpStatus.CONFLICT,
                "OPERATOR_APPLICATION_STATE_CONFLICT",
                "운영자 신청 상태가 요청과 일치하지 않습니다."
            ),
            Arguments.of(
                ErrorCode.INVALID_INPUT,
                HttpStatus.BAD_REQUEST,
                "INVALID_INPUT",
                "요청 값이 올바르지 않습니다."
            ),
            Arguments.of(
                ErrorCode.INVALID_JSON,
                HttpStatus.BAD_REQUEST,
                "INVALID_JSON",
                "요청 본문 형식이 올바르지 않습니다."
            ),
            Arguments.of(
                ErrorCode.INVALID_TYPE,
                HttpStatus.BAD_REQUEST,
                "INVALID_TYPE",
                "요청 값의 형식이 올바르지 않습니다."
            ),
            Arguments.of(
                ErrorCode.DUPLICATE_LOGIN_IDENTIFIER,
                HttpStatus.CONFLICT,
                "DUPLICATE_LOGIN_IDENTIFIER",
                "이미 사용 중인 이메일입니다."
            ),
            Arguments.of(
                ErrorCode.UNAUTHENTICATED,
                HttpStatus.UNAUTHORIZED,
                "UNAUTHENTICATED",
                "인증 정보가 없거나 유효하지 않습니다."
            ),
            Arguments.of(
                ErrorCode.FORBIDDEN,
                HttpStatus.FORBIDDEN,
                "FORBIDDEN",
                "접근 권한이 없습니다."
            ),
            Arguments.of(
                ErrorCode.NOT_FOUND,
                HttpStatus.NOT_FOUND,
                "NOT_FOUND",
                "요청한 리소스를 찾을 수 없습니다."
            ),
            Arguments.of(
                ErrorCode.METHOD_NOT_ALLOWED,
                HttpStatus.METHOD_NOT_ALLOWED,
                "METHOD_NOT_ALLOWED",
                "허용되지 않은 HTTP 메서드입니다."
            ),
            Arguments.of(
                ErrorCode.INTERNAL_SERVER_ERROR,
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_SERVER_ERROR",
                "서버 오류가 발생했습니다."
            )
        );
    }
}
