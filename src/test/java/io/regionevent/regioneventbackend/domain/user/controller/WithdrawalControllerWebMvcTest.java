package io.regionevent.regioneventbackend.domain.user.controller;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;

import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@WebMvcTest({
    LoginController.class,
    LogoutController.class,
    MyRoleController.class,
    SignupController.class,
    WithdrawalController.class
})
class WithdrawalControllerWebMvcTest extends UserControllerWebMvcTestSupport {

    private static final String WITHDRAWAL_PATH = "/api/v1/auth/delete";

    @Test
    void withdraw_인증된방문자_성공응답과토큰만료쿠키를반환한다() throws Exception {
        mockMvc.perform(authenticated(delete(WITHDRAWAL_PATH)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("회원탈퇴에 성공했습니다."))
            .andExpect(jsonPath("$.data").isEmpty())
            .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("refreshToken=")))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("Max-Age=0")))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("Path=/api/v1/auth")))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("HttpOnly")))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("Secure")))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("SameSite=Strict")));

        verify(withdrawUserUseCase).withdraw(AUTHENTICATED_USER_ID);
    }

    @Test
    void withdraw_탈퇴불가상태_권한오류를응답하고쿠키를변경하지않는다() throws Exception {
        doThrow(new BusinessException(ErrorCode.FORBIDDEN))
            .when(withdrawUserUseCase)
            .withdraw(AUTHENTICATED_USER_ID);

        mockMvc.perform(authenticated(delete(WITHDRAWAL_PATH)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"))
            .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void withdraw_인증정보없음_미인증오류를응답한다() throws Exception {
        mockMvc.perform(delete(WITHDRAWAL_PATH))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
            .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

}
