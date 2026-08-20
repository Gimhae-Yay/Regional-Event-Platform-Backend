package io.regionevent.regioneventbackend.domain.platformadmin.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.regionevent.regioneventbackend.domain.platformadmin.service.AdminAccountListInfo;
import io.regionevent.regioneventbackend.domain.platformadmin.service.GetAdminAccountsUseCase;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminAssignmentStatus;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminGrade;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.config.SecurityConfig;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.error.GlobalExceptionHandler;
import io.regionevent.regioneventbackend.global.security.access.AccessTokenAuthority;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;

@WebMvcTest(GetAdminAccountsController.class)
@Import({SecurityConfig.class, RequestIdFilter.class, GlobalExceptionHandler.class})
class GetAdminAccountsControllerWebMvcTest {

    private static final long ACTOR_USER_ID = 100L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAccessTokenService jwtAccessTokenService;

    @MockitoBean
    private GetAdminAccountsUseCase getAdminAccountsUseCase;

    @Test
    void getAdminAccounts_인가된전체관리자_정확한계정목록응답을반환한다() throws Exception {
        when(getAdminAccountsUseCase.get(ACTOR_USER_ID)).thenReturn(List.of(
            new AdminAccountListInfo(
                102L,
                "platform-admin@example.com",
                "플랫폼 관리자",
                PlatformAdminGrade.PLATFORM_ADMIN,
                PlatformAdminAssignmentStatus.INACTIVE,
                Instant.parse("2026-08-20T02:00:00Z"),
                Instant.parse("2026-08-20T03:00:00Z")
            ),
            new AdminAccountListInfo(
                101L,
                "super-admin@example.com",
                "슈퍼 관리자",
                PlatformAdminGrade.SUPER_ADMIN,
                PlatformAdminAssignmentStatus.ACTIVE,
                Instant.parse("2026-08-19T02:00:00Z"),
                null
            )
        ));

        mockMvc.perform(get("/api/v1/platform-admin/admin-accounts")
                .header(AUTHORIZATION, bearerToken(AccessTokenAuthority.PLATFORM_ADMIN)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("전체관리자 계정 목록 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.adminAccounts.length()").value(2))
            .andExpect(jsonPath("$.data.adminAccounts[0].length()").value(7))
            .andExpect(jsonPath("$.data.adminAccounts[0].userId").value("102"))
            .andExpect(jsonPath("$.data.adminAccounts[0].loginIdentifier")
                .value("platform-admin@example.com"))
            .andExpect(jsonPath("$.data.adminAccounts[0].name").value("플랫폼 관리자"))
            .andExpect(jsonPath("$.data.adminAccounts[0].grade").value("PLATFORM_ADMIN"))
            .andExpect(jsonPath("$.data.adminAccounts[0].status").value("INACTIVE"))
            .andExpect(jsonPath("$.data.adminAccounts[0].createdAt")
                .value("2026-08-20T02:00:00Z"))
            .andExpect(jsonPath("$.data.adminAccounts[0].inactivatedAt")
                .value("2026-08-20T03:00:00Z"))
            .andExpect(jsonPath("$.data.adminAccounts[1].inactivatedAt").value(nullValue()))
            .andExpect(jsonPath("$.data.adminAccounts[0].phone").doesNotExist())
            .andExpect(jsonPath("$.data.adminAccounts[0].passwordHash").doesNotExist())
            .andExpect(jsonPath("$.data.adminAccounts[0].inactiveReasonCode").doesNotExist());

        verify(getAdminAccountsUseCase).get(ACTOR_USER_ID);
    }

    @Test
    void getAdminAccounts_조회결과가없으면_빈배열을반환한다() throws Exception {
        when(getAdminAccountsUseCase.get(ACTOR_USER_ID)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/platform-admin/admin-accounts")
                .header(AUTHORIZATION, bearerToken(AccessTokenAuthority.SUPER_ADMIN)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.adminAccounts").isArray())
            .andExpect(jsonPath("$.data.adminAccounts").isEmpty());
    }

    @Test
    void getAdminAccounts_DB최종인가가부족하면_권한오류를반환한다() throws Exception {
        when(getAdminAccountsUseCase.get(ACTOR_USER_ID))
            .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        mockMvc.perform(get("/api/v1/platform-admin/admin-accounts")
                .header(AUTHORIZATION, bearerToken(AccessTokenAuthority.PLATFORM_ADMIN)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void getAdminAccounts_토큰authority가부족하면_유스케이스호출전권한오류를반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/platform-admin/admin-accounts")
                .header(AUTHORIZATION, bearerToken(AccessTokenAuthority.VISITOR)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(getAdminAccountsUseCase);
    }

    @Test
    void getAdminAccounts_인증정보가없으면_미인증오류를반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/platform-admin/admin-accounts"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verifyNoInteractions(getAdminAccountsUseCase);
    }

    private String bearerToken(AccessTokenAuthority authority) {
        return "Bearer " + jwtAccessTokenService.issue(ACTOR_USER_ID, List.of(authority));
    }
}
