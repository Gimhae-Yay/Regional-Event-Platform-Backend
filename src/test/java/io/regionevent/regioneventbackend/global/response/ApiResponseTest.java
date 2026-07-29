package io.regionevent.regioneventbackend.global.response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import io.regionevent.regioneventbackend.global.error.ErrorCode;

class ApiResponseTest {

    @Test
    void success_withData_createsSuccessResponse() {
        ApiResponse<String> response = ApiResponse.success(HttpStatus.OK, "조회에 성공했습니다.", "result");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.code()).isEqualTo("SUCCESS");
        assertThat(response.message()).isEqualTo("조회에 성공했습니다.");
        assertThat(response.data()).isEqualTo("result");
    }

    @Test
    void success_withoutData_setsDataToNull() {
        ApiResponse<Void> response = ApiResponse.success(HttpStatus.CREATED, "등록에 성공했습니다.");

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.code()).isEqualTo("SUCCESS");
        assertThat(response.data()).isNull();
    }

    @Test
    void fail_createsResponseFromErrorCode() {
        ApiResponse<Void> response = ApiResponse.fail(ErrorCode.INVALID_INPUT);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.code()).isEqualTo("INVALID_INPUT");
        assertThat(response.message()).isEqualTo("요청 값이 올바르지 않습니다.");
        assertThat(response.data()).isNull();
    }

    @Test
    void toResponseEntity_withAccessToken_addsAuthorizationHeader() {
        ApiResponse<String> response = ApiResponse.success(HttpStatus.OK, "로그인에 성공했습니다.", "profile");

        var responseEntity = response.toResponseEntity("access-token");

        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(responseEntity.getHeaders().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer access-token");
        assertThat(responseEntity.getBody()).isEqualTo(response);
    }

    @Test
    void constructor_whenSuccessResponseUsesErrorCode_throwsException() {
        assertThatThrownBy(() -> new ApiResponse<>(
            HttpStatus.OK.value(),
            ErrorCode.FORBIDDEN.code(),
            ErrorCode.FORBIDDEN.message(),
            "result"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_whenErrorResponseContainsData_throwsException() {
        assertThatThrownBy(() -> new ApiResponse<>(
            ErrorCode.FORBIDDEN.httpStatus().value(),
            ErrorCode.FORBIDDEN.code(),
            ErrorCode.FORBIDDEN.message(),
            "result"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_whenErrorResponseDoesNotMatchErrorCode_throwsException() {
        assertThatThrownBy(() -> new ApiResponse<>(
            ErrorCode.FORBIDDEN.httpStatus().value(),
            ErrorCode.FORBIDDEN.code(),
            "다른 메시지",
            null
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
