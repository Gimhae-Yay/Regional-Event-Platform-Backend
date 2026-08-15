package io.regionevent.regioneventbackend.domain.user.service;

import java.time.Clock;
import java.time.Duration;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.user.dto.RefreshAccessTokenResult;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;
import io.regionevent.regioneventbackend.global.security.refresh.InvalidRefreshTokenException;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshTokenService;

@Service
public class RefreshAccessTokenUseCase {

    private final AppUserService appUserService;
    private final JwtAccessTokenService jwtAccessTokenService;
    private final RefreshTokenService refreshTokenService;
    private final Clock clock;

    public RefreshAccessTokenUseCase(
        AppUserService appUserService,
        JwtAccessTokenService jwtAccessTokenService,
        RefreshTokenService refreshTokenService,
        Clock clock
    ) {
        this.appUserService = appUserService;
        this.jwtAccessTokenService = jwtAccessTokenService;
        this.refreshTokenService = refreshTokenService;
        this.clock = clock;
    }

    @Transactional
    public RefreshAccessTokenResult reissue(String refreshTokenValue) {
        if (refreshTokenValue == null || refreshTokenValue.isBlank()) {
            throw new InvalidRefreshTokenException();
        }

        RefreshTokenService.RotationResult<String> rotation = refreshTokenService.rotate(
            refreshTokenValue,
            currentToken -> {
                appUserService.findActiveUserForUpdate(currentToken.userId())
                    .orElseThrow(InvalidRefreshTokenException::new);
                return jwtAccessTokenService.issue(currentToken.userId());
            }
        );
        return new RefreshAccessTokenResult(
            rotation.preparationResult(),
            rotation.rotatedTokenValue(),
            Duration.between(clock.instant(), rotation.rotatedToken().expiresAt())
        );
    }
}
