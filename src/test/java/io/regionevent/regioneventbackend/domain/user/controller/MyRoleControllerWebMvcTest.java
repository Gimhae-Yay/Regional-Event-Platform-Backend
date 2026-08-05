package io.regionevent.regioneventbackend.domain.user.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;

import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.service.GetMyRoleAssignmentsUseCase;
import io.regionevent.regioneventbackend.domain.user.service.MyRoleAssignmentsResult;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@WebMvcTest({
    LoginController.class,
    LogoutController.class,
    MyRoleController.class,
    SignupController.class,
    WithdrawalController.class
})
class MyRoleControllerWebMvcTest extends UserControllerWebMvcTestSupport {

    private static final String MY_ROLE_PATH = "/api/v1/me";

    @Test
    void getMyRoles_방문자역할_지역없이역할목록을응답한다() throws Exception {
        when(getMyRoleAssignmentsUseCase.get(AUTHENTICATED_USER_ID)).thenReturn(new MyRoleAssignmentsResult(List.of(
            new MyRoleAssignmentsResult.RoleAssignment(UserRole.VISITOR, null, null)
        )));

        mockMvc.perform(authenticated(get(MY_ROLE_PATH)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("내 역할과 담당 지역 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.roleAssignments[0].role").value("VISITOR"))
            .andExpect(jsonPath("$.data.roleAssignments[0].regionId").isEmpty())
            .andExpect(jsonPath("$.data.roleAssignments[0].regionName").isEmpty());

        verify(getMyRoleAssignmentsUseCase).get(AUTHENTICATED_USER_ID);
    }

    @Test
    void getMyRoles_관할역할_지역식별자와이름을응답한다() throws Exception {
        when(getMyRoleAssignmentsUseCase.get(101L)).thenReturn(new MyRoleAssignmentsResult(List.of(
            new MyRoleAssignmentsResult.RoleAssignment(UserRole.REGION_ADMIN, 10L, "김해시")
        )));

        mockMvc.perform(authenticated(get(MY_ROLE_PATH), 101L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.roleAssignments[0].role").value("REGION_ADMIN"))
            .andExpect(jsonPath("$.data.roleAssignments[0].regionId").value("10"))
            .andExpect(jsonPath("$.data.roleAssignments[0].regionName").value("김해시"));
    }

    @Test
    void getMyRoles_탈퇴회원_권한오류를응답한다() throws Exception {
        when(getMyRoleAssignmentsUseCase.get(AUTHENTICATED_USER_ID))
            .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        mockMvc.perform(authenticated(get(MY_ROLE_PATH)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void getMyRoles_인증정보없음또는손상됨_미인증오류를응답한다() throws Exception {
        mockMvc.perform(get(MY_ROLE_PATH))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        mockMvc.perform(get(MY_ROLE_PATH).header("Authorization", "Bearer malformed"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

}
