package io.regionevent.regioneventbackend.domain.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;

import io.regionevent.regioneventbackend.domain.user.dto.SignupResponse;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@WebMvcTest({
    LoginController.class,
    LogoutController.class,
    MyRoleController.class,
    SignupController.class,
    WithdrawalController.class
})
class SignupControllerWebMvcTest extends UserControllerWebMvcTestSupport {

    @Test
    void signupVisitor_유효한요청_방문자회원가입응답을반환한다() throws Exception {
        when(signupUseCase.signup(any())).thenReturn(new SignupResponse("100", "VISITOR", "VISITOR", null));

        mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(visitorSignupRequest("visitor@example.com")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.statusCode").value(201))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("회원가입에 성공했습니다."))
            .andExpect(jsonPath("$.data.userId").value("100"))
            .andExpect(jsonPath("$.data.requestedRole").value("VISITOR"))
            .andExpect(jsonPath("$.data.assignedRole").value("VISITOR"))
            .andExpect(jsonPath("$.data.operatorApplicationStatus").isEmpty());

        verify(signupUseCase).signup(any());
    }

    @Test
    void signupOperator_유효한요청_운영자신청응답을반환한다() throws Exception {
        when(signupUseCase.signup(any())).thenReturn(new SignupResponse("101", "OPERATOR", null, "PENDING"));

        mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "operator@example.com",
                      "password": "LocalStamp!2026",
                      "name": "홍길동",
                      "phone": "01012345678",
                      "requestedRole": "OPERATOR",
                      "requestedRegionId": "1",
                      "businessInformation": "사업자등록번호 123-45-67890"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.userId").value("101"))
            .andExpect(jsonPath("$.data.requestedRole").value("OPERATOR"))
            .andExpect(jsonPath("$.data.assignedRole").isEmpty())
            .andExpect(jsonPath("$.data.operatorApplicationStatus").value("PENDING"));
    }

    @Test
    void signup_형식이유효하지않음_입력오류를응답하고유스케이스를호출하지않는다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "not-an-email",
                      "password": "short",
                      "name": "",
                      "phone": "010-12",
                      "requestedRole": "VISITOR"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(signupUseCase);
    }

    @Test
    void signup_중복이메일_충돌오류를응답한다() throws Exception {
        when(signupUseCase.signup(any())).thenThrow(new BusinessException(ErrorCode.DUPLICATE_LOGIN_IDENTIFIER));

        mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(visitorSignupRequest("duplicate@example.com")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("DUPLICATE_LOGIN_IDENTIFIER"));
    }

    private String visitorSignupRequest(String email) {
        return """
            {
              "email": "%s",
              "password": "LocalStamp!2026",
              "name": "홍길동",
              "phone": "01012345678",
              "requestedRole": "VISITOR"
            }
            """.formatted(email);
    }
}
