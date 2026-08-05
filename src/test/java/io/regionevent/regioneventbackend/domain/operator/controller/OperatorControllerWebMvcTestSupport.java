package io.regionevent.regioneventbackend.domain.operator.controller;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import io.regionevent.regioneventbackend.domain.operator.service.ApproveOperatorApplicationUseCase;
import io.regionevent.regioneventbackend.domain.operator.service.GetOperatorApplicationDetailUseCase;
import io.regionevent.regioneventbackend.domain.operator.service.GetPendingOperatorApplicationsUseCase;
import io.regionevent.regioneventbackend.domain.operator.service.ReapplyOperatorApplicationUseCase;
import io.regionevent.regioneventbackend.domain.operator.service.RejectOperatorApplicationUseCase;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.config.SecurityConfig;
import io.regionevent.regioneventbackend.global.error.GlobalExceptionHandler;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshTokenStore;

@Import({SecurityConfig.class, RequestIdFilter.class, GlobalExceptionHandler.class})
abstract class OperatorControllerWebMvcTestSupport {

    protected static final long REGION_ADMIN_ID = 100L;

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JwtAccessTokenService jwtAccessTokenService;

    @MockitoBean
    protected ApproveOperatorApplicationUseCase approveOperatorApplicationUseCase;

    @MockitoBean
    protected GetOperatorApplicationDetailUseCase getOperatorApplicationDetailUseCase;

    @MockitoBean
    protected GetPendingOperatorApplicationsUseCase getPendingOperatorApplicationsUseCase;

    @MockitoBean
    protected ReapplyOperatorApplicationUseCase reapplyOperatorApplicationUseCase;

    @MockitoBean
    protected RejectOperatorApplicationUseCase rejectOperatorApplicationUseCase;

    @MockitoBean
    protected RefreshTokenStore refreshTokenStore;

    protected MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder requestBuilder) {
        return requestBuilder.header(AUTHORIZATION, "Bearer " + jwtAccessTokenService.issue(REGION_ADMIN_ID));
    }
}
