package io.regionevent.regioneventbackend.domain.user.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.user.dto.LoginRequest;
import io.regionevent.regioneventbackend.domain.user.dto.LoginResponse;
import io.regionevent.regioneventbackend.domain.user.dto.LoginResult;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshTokenService;

@Service
public class LoginUseCase {

    private final AppUserService appUserService;
    private final UserRoleAssignmentService userRoleAssignmentService;
    private final JwtAccessTokenService jwtAccessTokenService;
    private final RefreshTokenService refreshTokenService;

    public LoginUseCase(
        AppUserService appUserService,
        UserRoleAssignmentService userRoleAssignmentService,
        JwtAccessTokenService jwtAccessTokenService,
        RefreshTokenService refreshTokenService
    ) {
        this.appUserService = appUserService;
        this.userRoleAssignmentService = userRoleAssignmentService;
        this.jwtAccessTokenService = jwtAccessTokenService;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public LoginResult login(LoginRequest request) {
        AppUser user = appUserService.authenticate(request.email(), request.password());
        List<String> roles = userRoleAssignmentService.findRolesByUserId(user.getUserId())
            .stream()
            .map(Enum::name)
            .toList();
        String refreshToken = refreshTokenService.issue(user.getUserId());
        String accessToken = jwtAccessTokenService.issue(user.getUserId());

        return new LoginResult(
            new LoginResponse(user.getUserId().toString(), roles),
            accessToken,
            refreshToken
        );
    }
}
