package io.regionevent.regioneventbackend.domain.review.controller;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import io.regionevent.regioneventbackend.domain.review.service.CreateVisitReviewUseCase;
import io.regionevent.regioneventbackend.domain.review.service.GetPublicContentReviewsUseCase;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.config.SecurityConfig;
import io.regionevent.regioneventbackend.global.error.GlobalExceptionHandler;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshTokenStore;

@Import({SecurityConfig.class, RequestIdFilter.class, GlobalExceptionHandler.class})
abstract class ReviewControllerWebMvcTestSupport {

    protected static final long AUTHENTICATED_USER_ID = 100L;

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JwtAccessTokenService jwtAccessTokenService;

    @MockitoBean
    protected CreateVisitReviewUseCase createVisitReviewUseCase;

    @MockitoBean
    protected GetPublicContentReviewsUseCase getPublicContentReviewsUseCase;

    @MockitoBean
    protected RefreshTokenStore refreshTokenStore;

    protected MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder requestBuilder) {
        return requestBuilder.header(AUTHORIZATION, "Bearer " + jwtAccessTokenService.issue(AUTHENTICATED_USER_ID));
    }
}
