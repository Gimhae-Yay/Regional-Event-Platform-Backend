package io.regionevent.regioneventbackend.domain.user.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.service.GetPlatformAdminUsersUseCase;
import io.regionevent.regioneventbackend.domain.user.service.PlatformAdminUserListInfo;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@WebMvcTest(PlatformAdminUserController.class)
class PlatformAdminUserControllerWebMvcTest extends UserControllerWebMvcTestSupport {

    @MockitoBean
    private GetPlatformAdminUsersUseCase getPlatformAdminUsersUseCase;

    @Test
    void getUsers_활성일반계정과역할목록을반환한다() throws Exception {
        when(getPlatformAdminUsersUseCase.get(AUTHENTICATED_USER_ID)).thenReturn(List.of(user()));

        mockMvc.perform(authenticated(get("/api/v1/platform-admin/users")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("사용자 목록 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.users[0].userId").value("200"))
            .andExpect(jsonPath("$.data.users[0].loginIdentifier").value("operator@example.com"))
            .andExpect(jsonPath("$.data.users[0].name").value("운영자"))
            .andExpect(jsonPath("$.data.users[0].roleAssignments[0].role").value("OPERATOR"))
            .andExpect(jsonPath("$.data.users[0].roleAssignments[0].regionId").value("11"))
            .andExpect(jsonPath("$.data.users[0].roleAssignments[0].regionName").value("김해시"))
            .andExpect(jsonPath("$.data.users[0].createdAt").value("2026-08-10T00:00:00Z"));

        verify(getPlatformAdminUsersUseCase).get(AUTHENTICATED_USER_ID);
    }

    @Test
    void getUsers_결과가없으면_빈배열을반환한다() throws Exception {
        when(getPlatformAdminUsersUseCase.get(AUTHENTICATED_USER_ID)).thenReturn(List.of());

        mockMvc.perform(authenticated(get("/api/v1/platform-admin/users")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.users").isArray())
            .andExpect(jsonPath("$.data.users").isEmpty());
    }

    @Test
    void getUsers_전체관리자가아니면_권한없음오류를반환한다() throws Exception {
        when(getPlatformAdminUsersUseCase.get(AUTHENTICATED_USER_ID))
            .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        mockMvc.perform(authenticated(get("/api/v1/platform-admin/users")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void getUsers_인증정보가없으면_미인증오류를반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/platform-admin/users"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    private PlatformAdminUserListInfo user() {
        return new PlatformAdminUserListInfo(
            200L,
            "operator@example.com",
            "운영자",
            List.of(new PlatformAdminUserListInfo.RoleAssignmentInfo(
                UserRole.OPERATOR,
                11L,
                "김해시"
            )),
            Instant.parse("2026-08-10T00:00:00Z")
        );
    }
}
