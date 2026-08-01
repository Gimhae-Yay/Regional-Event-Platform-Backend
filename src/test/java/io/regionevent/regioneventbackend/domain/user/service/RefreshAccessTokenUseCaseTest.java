package io.regionevent.regioneventbackend.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import io.regionevent.regioneventbackend.domain.user.dto.RefreshAccessTokenResult;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;
import io.regionevent.regioneventbackend.global.security.refresh.InvalidRefreshTokenException;
import io.regionevent.regioneventbackend.global.security.refresh.JwtRefreshTokenService;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshToken;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshTokenService;

class RefreshAccessTokenUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");

    private final AppUserService appUserService = mock(AppUserService.class);
    private final JwtAccessTokenService jwtAccessTokenService = mock(JwtAccessTokenService.class);
    private final JwtRefreshTokenService jwtRefreshTokenService = mock(JwtRefreshTokenService.class);
    private final RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
    private final RefreshAccessTokenUseCase refreshAccessTokenUseCase = new RefreshAccessTokenUseCase(
        appUserService,
        jwtAccessTokenService,
        jwtRefreshTokenService,
        refreshTokenService,
        Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Test
    void reissue_whenUserAndRefreshTokenAreValid_returnsNewTokens() {
        RefreshToken currentToken = refreshToken(NOW.plus(Duration.ofDays(14)));
        RefreshToken rotatedToken = refreshToken(NOW.plus(Duration.ofDays(7)));

        when(appUserService.findActiveUserForUpdate(currentToken.userId()))
            .thenReturn(Optional.of(mock(AppUser.class)));
        when(refreshTokenService.rotate(
            ArgumentMatchers.eq("current-token"),
            ArgumentMatchers.<Consumer<RefreshToken>>any()
        )).thenAnswer(invocation -> {
            Consumer<RefreshToken> beforeCompletion = invocation.getArgument(1);
            beforeCompletion.accept(currentToken);
            return "rotated-token";
        });
        when(jwtRefreshTokenService.authenticate("rotated-token"))
            .thenReturn(rotatedToken);
        when(jwtAccessTokenService.issue(rotatedToken.userId())).thenReturn("access-token");

        RefreshAccessTokenResult result = refreshAccessTokenUseCase.reissue("current-token");

        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("rotated-token");
        assertThat(result.refreshTokenMaxAge()).isEqualTo(Duration.ofDays(7));
        assertThat(result).hasToString("RefreshAccessTokenResult[redacted]");
        verify(refreshTokenService).rotate(
            ArgumentMatchers.eq("current-token"),
            ArgumentMatchers.<Consumer<RefreshToken>>any()
        );
    }

    @Test
    void reissue_whenUserIsNotActive_doesNotRotateToken() {
        RefreshToken currentToken = refreshToken(NOW.plus(Duration.ofDays(14)));

        when(appUserService.findActiveUserForUpdate(currentToken.userId()))
            .thenReturn(Optional.empty());
        when(refreshTokenService.rotate(
            ArgumentMatchers.eq("current-token"),
            ArgumentMatchers.<Consumer<RefreshToken>>any()
        )).thenAnswer(invocation -> {
            Consumer<RefreshToken> beforeCompletion = invocation.getArgument(1);
            beforeCompletion.accept(currentToken);
            return "rotated-token";
        });

        assertThatThrownBy(() -> refreshAccessTokenUseCase.reissue("current-token"))
            .isInstanceOf(InvalidRefreshTokenException.class);

        verifyNoInteractions(jwtAccessTokenService);
    }

    private RefreshToken refreshToken(Instant expiresAt) {
        return new RefreshToken(
            1L,
            UUID.randomUUID(),
            UUID.randomUUID(),
            NOW,
            expiresAt
        );
    }
}
