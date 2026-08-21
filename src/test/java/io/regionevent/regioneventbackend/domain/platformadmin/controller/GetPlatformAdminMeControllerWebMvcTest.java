package io.regionevent.regioneventbackend.domain.platformadmin.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.regionevent.regioneventbackend.domain.platformadmin.service.GetPlatformAdminMeUseCase;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.config.SecurityConfig;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.error.GlobalExceptionHandler;
import io.regionevent.regioneventbackend.global.security.access.AccessTokenAuthority;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;

@WebMvcTest(GetPlatformAdminMeController.class)
@Import({SecurityConfig.class, RequestIdFilter.class, GlobalExceptionHandler.class})
class GetPlatformAdminMeControllerWebMvcTest {

    private static final long ACTOR_USER_ID = 100L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAccessTokenService jwtAccessTokenService;

    @MockitoBean
    private GetPlatformAdminMeUseCase getPlatformAdminMeUseCase;

    @ParameterizedTest
    @EnumSource(
        value = AccessTokenAuthority.class,
        names = {"SUPER_ADMIN", "PLATFORM_ADMIN"}
    )
    void getPlatformAdminMe_인가된전체관리자_토큰등급의본인정보만반환한다(
        AccessTokenAuthority authority
    ) throws Exception {
        when(getPlatformAdminMeUseCase.get(ACTOR_USER_ID)).thenReturn(ACTOR_USER_ID);

        mockMvc.perform(get("/api/v1/platform-admin/me")
                .header(AUTHORIZATION, bearerToken(authority)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("전체관리자 본인 권한 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.data.userId").value("100"))
            .andExpect(jsonPath("$.data.grade").value(authority.name()))
            .andExpect(jsonPath("$.data.status").doesNotExist())
            .andExpect(jsonPath("$.data.loginIdentifier").doesNotExist())
            .andExpect(jsonPath("$.data.roleAssignments").doesNotExist())
            .andExpect(jsonPath("$.data.accessToken").doesNotExist())
            .andExpect(jsonPath("$.data.refreshToken").doesNotExist());

        verify(getPlatformAdminMeUseCase).get(ACTOR_USER_ID);
    }

    @Test
    void getPlatformAdminMe_DB최종인가가부족하면_권한오류를반환한다() throws Exception {
        when(getPlatformAdminMeUseCase.get(ACTOR_USER_ID))
            .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        mockMvc.perform(get("/api/v1/platform-admin/me")
                .header(AUTHORIZATION, bearerToken(AccessTokenAuthority.PLATFORM_ADMIN)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void getPlatformAdminMe_토큰authority가부족하면_유스케이스호출전권한오류를반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/platform-admin/me")
                .header(AUTHORIZATION, bearerToken(AccessTokenAuthority.VISITOR)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(getPlatformAdminMeUseCase);
    }

    @Test
    void getPlatformAdminMe_인증정보가없으면_미인증오류를반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/platform-admin/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verifyNoInteractions(getPlatformAdminMeUseCase);
    }

    @Test
    void getPlatformAdminMe_토큰이유효하지않으면_미인증오류를반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/platform-admin/me")
                .header(AUTHORIZATION, "Bearer malformed"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verifyNoInteractions(getPlatformAdminMeUseCase);
    }

    @Test
    void getPlatformAdminMe_예상하지못한오류가발생하면_서버오류를반환한다() throws Exception {
        when(getPlatformAdminMeUseCase.get(ACTOR_USER_ID))
            .thenThrow(new IllegalStateException("unexpected"));

        mockMvc.perform(get("/api/v1/platform-admin/me")
                .header(AUTHORIZATION, bearerToken(AccessTokenAuthority.SUPER_ADMIN)))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));
    }

    private String bearerToken(AccessTokenAuthority authority) {
        return "Bearer " + jwtAccessTokenService.issue(ACTOR_USER_ID, List.of(authority));
    }
}
