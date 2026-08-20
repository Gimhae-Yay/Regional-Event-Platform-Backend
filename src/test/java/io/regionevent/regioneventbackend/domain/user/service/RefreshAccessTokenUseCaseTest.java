package io.regionevent.regioneventbackend.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;
import io.regionevent.regioneventbackend.global.security.refresh.InvalidRefreshTokenException;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshToken;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshTokenService;

class RefreshAccessTokenUseCaseTest {

    private final AppUserService appUserService = mock(AppUserService.class);
    private final AccessTokenAuthorityResolver accessTokenAuthorityResolver = mock(AccessTokenAuthorityResolver.class);
    private final JwtAccessTokenService jwtAccessTokenService = mock(JwtAccessTokenService.class);
    private final RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
    private final RefreshAccessTokenUseCase refreshAccessTokenUseCase = new RefreshAccessTokenUseCase(
        appUserService,
        accessTokenAuthorityResolver,
        jwtAccessTokenService,
        refreshTokenService
    );

    @Test
    void reissue_유효한토큰과활성사용자_AccessToken을반환한다() {
        RefreshToken refreshToken = refreshToken();
        AppUser user = mock(AppUser.class);
        when(refreshTokenService.authenticate("current-token")).thenReturn(refreshToken);
        when(appUserService.findActiveUserForUpdate(refreshToken.userId())).thenReturn(Optional.of(user));
        when(accessTokenAuthorityResolver.resolve(user)).thenReturn(List.of());
        when(jwtAccessTokenService.issue(eq(refreshToken.userId()), anyList())).thenReturn("access-token");

        var result = refreshAccessTokenUseCase.reissue("current-token");

        assertThat(result.accessToken()).isEqualTo("access-token");
        verify(jwtAccessTokenService).issue(refreshToken.userId(), List.of());
    }

    @Test
    void reissue_비활성사용자_AccessToken을발급하지않는다() {
        RefreshToken refreshToken = refreshToken();
        when(refreshTokenService.authenticate("current-token")).thenReturn(refreshToken);
        when(appUserService.findActiveUserForUpdate(refreshToken.userId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refreshAccessTokenUseCase.reissue("current-token"))
            .isInstanceOf(InvalidRefreshTokenException.class);

        verifyNoInteractions(jwtAccessTokenService);
    }

    private RefreshToken refreshToken() {
        Instant issuedAt = Instant.parse("2026-08-01T00:00:00Z");
        return new RefreshToken(1L, issuedAt, issuedAt.plusSeconds(1_209_600));
    }
}
