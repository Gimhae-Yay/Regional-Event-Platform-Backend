package io.regionevent.regioneventbackend.domain.reservation.controller;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import io.regionevent.regioneventbackend.domain.reservation.service.CreateReservationHoldUseCase;
import io.regionevent.regioneventbackend.domain.reservation.service.GetMyReservationQrUseCase;
import io.regionevent.regioneventbackend.domain.reservation.service.GetSessionReservationsUseCase;
import io.regionevent.regioneventbackend.domain.reservation.service.ReservationCancellationUseCase;
import io.regionevent.regioneventbackend.domain.reservation.service.ReservationConfirmationUseCase;
import io.regionevent.regioneventbackend.domain.reservation.service.SearchOperatorReservationByNumberUseCase;
import io.regionevent.regioneventbackend.domain.reservation.service.SearchRegionAdminReservationByNumberUseCase;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.config.SecurityConfig;
import io.regionevent.regioneventbackend.global.error.GlobalExceptionHandler;
import io.regionevent.regioneventbackend.global.security.access.AccessTokenTestFactory;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshTokenStore;

@Import({SecurityConfig.class, RequestIdFilter.class, GlobalExceptionHandler.class})
abstract class ReservationControllerWebMvcTestSupport {

    protected static final long AUTHENTICATED_USER_ID = 100L;

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JwtAccessTokenService jwtAccessTokenService;

    @MockitoBean
    protected CreateReservationHoldUseCase createReservationHoldUseCase;

    @MockitoBean
    protected GetMyReservationQrUseCase getMyReservationQrUseCase;

    @MockitoBean
    protected GetSessionReservationsUseCase getSessionReservationsUseCase;

    @MockitoBean
    protected ReservationCancellationUseCase reservationCancellationUseCase;

    @MockitoBean
    protected ReservationConfirmationUseCase reservationConfirmationUseCase;

    @MockitoBean
    protected SearchOperatorReservationByNumberUseCase searchOperatorReservationByNumberUseCase;

    @MockitoBean
    protected SearchRegionAdminReservationByNumberUseCase searchRegionAdminReservationByNumberUseCase;

    @MockitoBean
    protected RefreshTokenStore refreshTokenStore;

    protected MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder requestBuilder) {
        return requestBuilder.header(AUTHORIZATION, "Bearer " + AccessTokenTestFactory.issueForAuthenticatedRequest(jwtAccessTokenService, AUTHENTICATED_USER_ID));
    }
}
