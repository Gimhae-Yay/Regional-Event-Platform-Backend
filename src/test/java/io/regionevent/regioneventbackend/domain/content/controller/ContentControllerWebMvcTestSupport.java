package io.regionevent.regioneventbackend.domain.content.controller;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;

import javax.sql.DataSource;

import jakarta.persistence.EntityManagerFactory;

import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.config.SecurityConfig;
import io.regionevent.regioneventbackend.global.error.GlobalExceptionHandler;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshTokenStore;
import io.regionevent.regioneventbackend.domain.content.service.ApproveContentRevisionUseCase;
import io.regionevent.regioneventbackend.domain.content.service.ApproveContentSessionUseCase;
import io.regionevent.regioneventbackend.domain.content.service.ApproveContentUseCase;
import io.regionevent.regioneventbackend.domain.content.service.CancelContentSessionUseCase;
import io.regionevent.regioneventbackend.domain.content.service.ContentSessionService;
import io.regionevent.regioneventbackend.domain.content.service.CreateContentRevisionUseCase;
import io.regionevent.regioneventbackend.domain.content.service.CreateContentUseCase;
import io.regionevent.regioneventbackend.domain.content.service.DeleteContentUseCase;
import io.regionevent.regioneventbackend.domain.content.service.EndContentReservationsUseCase;
import io.regionevent.regioneventbackend.domain.content.service.GetContentHistoryUseCase;
import io.regionevent.regioneventbackend.domain.content.service.GetContentRevisionReviewDetailUseCase;
import io.regionevent.regioneventbackend.domain.content.service.GetMyContentUseCase;
import io.regionevent.regioneventbackend.domain.content.service.GetOriginalContentReviewDetailUseCase;
import io.regionevent.regioneventbackend.domain.content.service.GetPendingContentsUseCase;
import io.regionevent.regioneventbackend.domain.content.service.GetPendingContentRevisionsUseCase;
import io.regionevent.regioneventbackend.domain.content.service.GetPendingSessionReviewDetailUseCase;
import io.regionevent.regioneventbackend.domain.content.service.GetPendingSessionRevisionsUseCase;
import io.regionevent.regioneventbackend.domain.content.service.GetPublicContentSessionsUseCase;
import io.regionevent.regioneventbackend.domain.content.service.GetPublicContentUseCase;
import io.regionevent.regioneventbackend.domain.content.service.GetPublicContentsUseCase;
import io.regionevent.regioneventbackend.domain.content.service.GetSessionRevisionReviewDetailUseCase;
import io.regionevent.regioneventbackend.domain.content.service.RejectContentRevisionUseCase;
import io.regionevent.regioneventbackend.domain.content.service.RejectContentSessionUseCase;
import io.regionevent.regioneventbackend.domain.content.service.RejectContentUseCase;
import io.regionevent.regioneventbackend.domain.content.service.RejectSessionRevisionUseCase;
import io.regionevent.regioneventbackend.domain.content.service.SubmitContentUseCase;
import io.regionevent.regioneventbackend.domain.content.service.UpdateContentRevisionUseCase;
import io.regionevent.regioneventbackend.domain.content.service.UpdateMyContentUseCase;
import io.regionevent.regioneventbackend.domain.content.service.WithdrawContentRevisionUseCase;

@Import({
    SecurityConfig.class,
    RequestIdFilter.class,
    GlobalExceptionHandler.class
})
abstract class ContentControllerWebMvcTestSupport {

    protected static final long AUTHENTICATED_USER_ID = 100L;

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ApplicationContext applicationContext;

    @Autowired
    private JwtAccessTokenService jwtAccessTokenService;

    @MockitoBean
    private RefreshTokenStore refreshTokenStore;

    @MockitoBean
    protected ApproveContentRevisionUseCase approveContentRevisionUseCase;

    @MockitoBean
    protected ApproveContentSessionUseCase approveContentSessionUseCase;

    @MockitoBean
    protected ApproveContentUseCase approveContentUseCase;

    @MockitoBean
    protected CancelContentSessionUseCase cancelContentSessionUseCase;

    @MockitoBean
    protected ContentSessionService contentSessionService;

    @MockitoBean
    protected CreateContentRevisionUseCase createContentRevisionUseCase;

    @MockitoBean
    protected CreateContentUseCase createContentUseCase;

    @MockitoBean
    protected DeleteContentUseCase deleteContentUseCase;

    @MockitoBean
    protected EndContentReservationsUseCase endContentReservationsUseCase;

    @MockitoBean
    protected GetContentHistoryUseCase getContentHistoryUseCase;

    @MockitoBean
    protected GetContentRevisionReviewDetailUseCase getContentRevisionReviewDetailUseCase;

    @MockitoBean
    protected GetMyContentUseCase getMyContentUseCase;

    @MockitoBean
    protected GetOriginalContentReviewDetailUseCase getOriginalContentReviewDetailUseCase;

    @MockitoBean
    protected GetPendingContentsUseCase getPendingContentsUseCase;

    @MockitoBean
    protected GetPendingContentRevisionsUseCase getPendingContentRevisionsUseCase;

    @MockitoBean
    protected GetPendingSessionReviewDetailUseCase getPendingSessionReviewDetailUseCase;

    @MockitoBean
    protected GetPendingSessionRevisionsUseCase getPendingSessionRevisionsUseCase;

    @MockitoBean
    protected GetPublicContentSessionsUseCase getPublicContentSessionsUseCase;

    @MockitoBean
    protected GetPublicContentUseCase getPublicContentUseCase;

    @MockitoBean
    protected GetPublicContentsUseCase getPublicContentsUseCase;

    @MockitoBean
    protected GetSessionRevisionReviewDetailUseCase getSessionRevisionReviewDetailUseCase;

    @MockitoBean
    protected RejectContentRevisionUseCase rejectContentRevisionUseCase;

    @MockitoBean
    protected RejectContentSessionUseCase rejectContentSessionUseCase;

    @MockitoBean
    protected RejectContentUseCase rejectContentUseCase;

    @MockitoBean
    protected RejectSessionRevisionUseCase rejectSessionRevisionUseCase;

    @MockitoBean
    protected SubmitContentUseCase submitContentUseCase;

    @MockitoBean
    protected UpdateContentRevisionUseCase updateContentRevisionUseCase;

    @MockitoBean
    protected UpdateMyContentUseCase updateMyContentUseCase;

    @MockitoBean
    protected WithdrawContentRevisionUseCase withdrawContentRevisionUseCase;

    protected MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder requestBuilder) {
        return authenticated(requestBuilder, AUTHENTICATED_USER_ID);
    }

    protected MockHttpServletRequestBuilder authenticated(
        MockHttpServletRequestBuilder requestBuilder,
        long userId
    ) {
        return requestBuilder.header(AUTHORIZATION, "Bearer " + jwtAccessTokenService.issue(userId));
    }

    protected boolean hasDatabaseInfrastructure() {
        return hasBean(DataSource.class)
            || hasBean(Flyway.class)
            || hasBean(EntityManagerFactory.class)
            || hasBean(HikariDataSource.class);
    }

    private boolean hasBean(Class<?> beanType) {
        return applicationContext.getBeansOfType(beanType).size() > 0;
    }
}
