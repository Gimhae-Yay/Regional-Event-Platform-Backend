package io.regionevent.regioneventbackend.domain.user.controller;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import io.regionevent.regioneventbackend.domain.user.service.GetMyRoleAssignmentsUseCase;
import io.regionevent.regioneventbackend.domain.user.service.LoginUseCase;
import io.regionevent.regioneventbackend.domain.user.service.SignupUseCase;
import io.regionevent.regioneventbackend.domain.user.service.WithdrawUserUseCase;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.config.SecurityConfig;
import io.regionevent.regioneventbackend.global.error.GlobalExceptionHandler;
import io.regionevent.regioneventbackend.global.security.access.AccessTokenTestFactory;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshTokenService;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshTokenStore;

@Import({SecurityConfig.class, RequestIdFilter.class, GlobalExceptionHandler.class})
abstract class UserControllerWebMvcTestSupport {

    protected static final long AUTHENTICATED_USER_ID = 100L;

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JwtAccessTokenService jwtAccessTokenService;

    @Autowired
    protected RefreshTokenService refreshTokenService;

    @MockitoBean
    protected LoginUseCase loginUseCase;

    @MockitoBean
    protected GetMyRoleAssignmentsUseCase getMyRoleAssignmentsUseCase;

    @MockitoBean
    protected SignupUseCase signupUseCase;

    @MockitoBean
    protected WithdrawUserUseCase withdrawUserUseCase;

    @MockitoBean
    protected RefreshTokenStore refreshTokenStore;

    protected MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder requestBuilder) {
        return authenticated(requestBuilder, AUTHENTICATED_USER_ID);
    }

    protected MockHttpServletRequestBuilder authenticated(
        MockHttpServletRequestBuilder requestBuilder,
        long userId
    ) {
        return requestBuilder.header(AUTHORIZATION, "Bearer " + AccessTokenTestFactory.issueForAuthenticatedRequest(jwtAccessTokenService, userId));
    }
}
