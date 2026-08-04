package io.regionevent.regioneventbackend.domain.user.service;

import java.time.Clock;
import java.time.Duration;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.user.dto.RefreshAccessTokenResult;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;
import io.regionevent.regioneventbackend.global.security.refresh.InvalidRefreshTokenException;
import io.regionevent.regioneventbackend.global.security.refresh.JwtRefreshTokenService;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshToken;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshTokenService;

@Service
public class RefreshAccessTokenUseCase {

    private final AppUserService appUserService;
    private final JwtAccessTokenService jwtAccessTokenService;
    private final JwtRefreshTokenService jwtRefreshTokenService;
    private final RefreshTokenService refreshTokenService;
    private final Clock clock;

    public RefreshAccessTokenUseCase(
        AppUserService appUserService,
        JwtAccessTokenService jwtAccessTokenService,
        JwtRefreshTokenService jwtRefreshTokenService,
        RefreshTokenService refreshTokenService,
        Clock clock
    ) {
        this.appUserService = appUserService;
        this.jwtAccessTokenService = jwtAccessTokenService;
        this.jwtRefreshTokenService = jwtRefreshTokenService;
        this.refreshTokenService = refreshTokenService;
        this.clock = clock;
    }

    @Transactional
    public RefreshAccessTokenResult reissue(String refreshTokenValue) {
        if (refreshTokenValue == null || refreshTokenValue.isBlank()) {
            throw new InvalidRefreshTokenException();
        }

        String rotatedTokenValue = refreshTokenService.rotate(
            refreshTokenValue,
            currentToken -> appUserService.findActiveUserForUpdate(currentToken.userId())
                .orElseThrow(InvalidRefreshTokenException::new)
        );
        RefreshToken rotatedToken = jwtRefreshTokenService.authenticate(rotatedTokenValue);
        return new RefreshAccessTokenResult(
            jwtAccessTokenService.issue(rotatedToken.userId()),
            rotatedTokenValue,
            Duration.between(clock.instant(), rotatedToken.expiresAt())
        );
    }
}
