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
import io.regionevent.regioneventbackend.global.security.access.AccessTokenTestFactory;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;
import io.regionevent.regioneventbackend.domain.content.service.ApproveContentRevisionUseCase;
import io.regionevent.regioneventbackend.domain.content.service.ApproveContentSessionUseCase;
import io.regionevent.regioneventbackend.domain.content.service.ApproveContentUseCase;
import io.regionevent.regioneventbackend.domain.content.service.ApproveContentWithdrawalUseCase;
import io.regionevent.regioneventbackend.domain.content.service.CancelContentSessionUseCase;
import io.regionevent.regioneventbackend.domain.content.service.ContentSessionService;
import io.regionevent.regioneventbackend.domain.content.service.CreateContentRevisionUseCase;
import io.regionevent.regioneventbackend.domain.content.service.CreateContentSessionUseCase;
import io.regionevent.regioneventbackend.domain.content.service.CreateContentUseCase;
import io.regionevent.regioneventbackend.domain.content.service.CreateSessionRevisionUseCase;
import io.regionevent.regioneventbackend.domain.content.service.DeleteContentUseCase;
import io.regionevent.regioneventbackend.domain.content.service.EndContentReservationsUseCase;
import io.regionevent.regioneventbackend.domain.content.service.GetContentHistoryUseCase;
import io.regionevent.regioneventbackend.domain.content.service.GetContentRevisionReviewDetailUseCase;
import io.regionevent.regioneventbackend.domain.content.service.GetContentWithdrawalReviewDetailUseCase;
import io.regionevent.regioneventbackend.domain.content.service.GetLatestContentRevisionUseCase;
import io.regionevent.regioneventbackend.domain.content.service.GetMyContentUseCase;
import io.regionevent.regioneventbackend.domain.content.service.GetOriginalContentReviewDetailUseCase;
import io.regionevent.regioneventbackend.domain.content.service.GetPendingContentWithdrawalRequestsUseCase;
import io.regionevent.regioneventbackend.domain.content.service.GetPendingContentRevisionsUseCase;
import io.regionevent.regioneventbackend.domain.content.service.GetPendingSessionReviewDetailUseCase;
import io.regionevent.regioneventbackend.domain.content.service.GetPendingSessionRevisionsUseCase;
import io.regionevent.regioneventbackend.domain.content.service.GetPublicContentSessionsUseCase;
import io.regionevent.regioneventbackend.domain.content.service.GetPublicContentUseCase;
import io.regionevent.regioneventbackend.domain.content.service.GetPublicContentsUseCase;
import io.regionevent.regioneventbackend.domain.content.service.GetRegionAdminContentsUseCase;
import io.regionevent.regioneventbackend.domain.content.service.GetSessionRevisionReviewDetailUseCase;
import io.regionevent.regioneventbackend.domain.content.service.RejectContentRevisionUseCase;
import io.regionevent.regioneventbackend.domain.content.service.RejectContentSessionUseCase;
import io.regionevent.regioneventbackend.domain.content.service.RejectContentUseCase;
import io.regionevent.regioneventbackend.domain.content.service.RejectContentWithdrawalUseCase;
import io.regionevent.regioneventbackend.domain.content.service.RejectSessionRevisionUseCase;
import io.regionevent.regioneventbackend.domain.content.service.RequestContentWithdrawalUseCase;
import io.regionevent.regioneventbackend.domain.content.service.ResubmitContentRevisionUseCase;
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
    protected ApproveContentRevisionUseCase approveContentRevisionUseCase;

    @MockitoBean
    protected ApproveContentSessionUseCase approveContentSessionUseCase;

    @MockitoBean
    protected ApproveContentUseCase approveContentUseCase;

    @MockitoBean
    protected ApproveContentWithdrawalUseCase approveContentWithdrawalUseCase;

    @MockitoBean
    protected CancelContentSessionUseCase cancelContentSessionUseCase;

    @MockitoBean
    protected ContentSessionService contentSessionService;

    @MockitoBean
    protected CreateContentRevisionUseCase createContentRevisionUseCase;

    @MockitoBean
    protected CreateContentSessionUseCase createContentSessionUseCase;

    @MockitoBean
    protected CreateSessionRevisionUseCase createSessionRevisionUseCase;

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
    protected GetContentWithdrawalReviewDetailUseCase getContentWithdrawalReviewDetailUseCase;

    @MockitoBean
    protected GetLatestContentRevisionUseCase getLatestContentRevisionUseCase;

    @MockitoBean
    protected GetMyContentUseCase getMyContentUseCase;

    @MockitoBean
    protected GetOriginalContentReviewDetailUseCase getOriginalContentReviewDetailUseCase;

    @MockitoBean
    protected GetRegionAdminContentsUseCase getRegionAdminContentsUseCase;

    @MockitoBean
    protected GetPendingContentWithdrawalRequestsUseCase getPendingContentWithdrawalRequestsUseCase;

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
    protected RejectContentWithdrawalUseCase rejectContentWithdrawalUseCase;

    @MockitoBean
    protected RejectSessionRevisionUseCase rejectSessionRevisionUseCase;

    @MockitoBean
    protected RequestContentWithdrawalUseCase requestContentWithdrawalUseCase;

    @MockitoBean
    protected ResubmitContentRevisionUseCase resubmitContentRevisionUseCase;

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
        return requestBuilder.header(AUTHORIZATION, "Bearer " + AccessTokenTestFactory.issueForAuthenticatedRequest(jwtAccessTokenService, userId));
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
