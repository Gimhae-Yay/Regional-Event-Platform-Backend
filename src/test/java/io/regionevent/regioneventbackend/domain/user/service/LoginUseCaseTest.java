package io.regionevent.regioneventbackend.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.user.dto.LoginRequest;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshTokenService;

class LoginUseCaseTest {

    private final AppUserService appUserService = mock(AppUserService.class);
    private final UserRoleAssignmentService userRoleAssignmentService = mock(UserRoleAssignmentService.class);
    private final AccessTokenAuthorityResolver accessTokenAuthorityResolver = mock(AccessTokenAuthorityResolver.class);
    private final JwtAccessTokenService jwtAccessTokenService = mock(JwtAccessTokenService.class);
    private final RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
    private final LoginUseCase loginUseCase = new LoginUseCase(
        appUserService,
        userRoleAssignmentService,
        accessTokenAuthorityResolver,
        jwtAccessTokenService,
        refreshTokenService
    );

    @Test
    void login_whenPendingOperatorHasNoActiveRole_issuesTokensWithEmptyAuthorities() {
        AppUser user = mock(AppUser.class);
        when(user.getUserId()).thenReturn(1L);
        when(appUserService.authenticate("operator@example.com", "LocalStamp!2026")).thenReturn(user);
        when(userRoleAssignmentService.findRolesByUserId(1L)).thenReturn(List.of());
        when(accessTokenAuthorityResolver.resolve(user)).thenReturn(List.of());
        when(refreshTokenService.issue(1L)).thenReturn("refresh-token");
        when(jwtAccessTokenService.issue(eq(1L), anyList())).thenReturn("access-token");

        var result = loginUseCase.login(new LoginRequest("operator@example.com", "LocalStamp!2026"));

        assertThat(result.response().roles()).isEmpty();
        assertThat(result.response().accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        verify(jwtAccessTokenService).issue(1L, List.of());
    }

    @Test
    void login_whenAuthoritySourcesConflict_rejectsBeforeIssuingRefreshToken() {
        AppUser user = mock(AppUser.class);
        when(user.getUserId()).thenReturn(1L);
        when(appUserService.authenticate("user@example.com", "LocalStamp!2026")).thenReturn(user);
        when(userRoleAssignmentService.findRolesByUserId(1L)).thenReturn(List.of(UserRole.VISITOR));
        when(accessTokenAuthorityResolver.resolve(user)).thenThrow(new AccessTokenAuthoritySourceConflictException());

        assertThatThrownBy(() -> loginUseCase.login(new LoginRequest("user@example.com", "LocalStamp!2026")))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.INVALID_CREDENTIALS);

        verifyNoInteractions(jwtAccessTokenService, refreshTokenService);
    }
}
