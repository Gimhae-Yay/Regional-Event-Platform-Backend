package io.regionevent.regioneventbackend.domain.user.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.user.dto.RefreshAccessTokenResult;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;
import io.regionevent.regioneventbackend.global.security.refresh.InvalidRefreshTokenException;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshToken;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshTokenService;

@Service
public class RefreshAccessTokenUseCase {

    private final AppUserService appUserService;
    private final AccessTokenAuthorityResolver accessTokenAuthorityResolver;
    private final JwtAccessTokenService jwtAccessTokenService;
    private final RefreshTokenService refreshTokenService;

    public RefreshAccessTokenUseCase(
        AppUserService appUserService,
        AccessTokenAuthorityResolver accessTokenAuthorityResolver,
        JwtAccessTokenService jwtAccessTokenService,
        RefreshTokenService refreshTokenService
    ) {
        this.appUserService = appUserService;
        this.accessTokenAuthorityResolver = accessTokenAuthorityResolver;
        this.jwtAccessTokenService = jwtAccessTokenService;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public RefreshAccessTokenResult reissue(String refreshTokenValue) {
        if (refreshTokenValue == null || refreshTokenValue.isBlank()) {
            throw new InvalidRefreshTokenException();
        }

        RefreshToken refreshToken = refreshTokenService.authenticate(refreshTokenValue);
        var user = appUserService.findActiveUserForUpdate(refreshToken.userId())
            .orElseThrow(InvalidRefreshTokenException::new);
        try {
            return new RefreshAccessTokenResult(
                jwtAccessTokenService.issue(refreshToken.userId(), accessTokenAuthorityResolver.resolve(user))
            );
        } catch (AccessTokenAuthoritySourceConflictException exception) {
            throw new InvalidRefreshTokenException(exception);
        }
    }
}
