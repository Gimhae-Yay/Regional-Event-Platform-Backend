package io.regionevent.regioneventbackend.domain.payment.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.regionevent.regioneventbackend.domain.payment.entity.RefundStatus;
import io.regionevent.regioneventbackend.domain.payment.service.GetRefundFailuresUseCase;
import io.regionevent.regioneventbackend.domain.payment.service.RefundFailureListInfo;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.config.SecurityConfig;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.error.GlobalExceptionHandler;
import io.regionevent.regioneventbackend.global.security.access.AccessTokenTestFactory;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshTokenStore;

@WebMvcTest(RefundFailureController.class)
@Import({SecurityConfig.class, RequestIdFilter.class, GlobalExceptionHandler.class})
class RefundFailureControllerWebMvcTest {

    private static final long USER_ID = 100L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAccessTokenService jwtAccessTokenService;

    @MockitoBean
    private GetRefundFailuresUseCase getRefundFailuresUseCase;

    @MockitoBean
    private RefreshTokenStore refreshTokenStore;

    @Test
    void getFailures_기본상태로_실패환불목록을_반환한다() throws Exception {
        when(getRefundFailuresUseCase.get(eq(USER_ID), any())).thenReturn(List.of(refund()));

        mockMvc.perform(authenticated(get("/api/v1/platform-admin/refund-failures")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.refunds[0].refundId").value("552"))
            .andExpect(jsonPath("$.data.refunds[0].paymentId").value("903"))
            .andExpect(jsonPath("$.data.refunds[0].reservationId").value("124"))
            .andExpect(jsonPath("$.data.refunds[0].amount").value(12_000))
            .andExpect(jsonPath("$.data.refunds[0].currency").value("KRW"))
            .andExpect(jsonPath("$.data.refunds[0].status").value("DISCREPANT"))
            .andExpect(jsonPath("$.data.refunds[0].attemptCount").value(1))
            .andExpect(jsonPath("$.data.refunds[0].requestedAt").value("2026-08-07T01:10:00Z"))
            .andExpect(jsonPath("$.data.refunds[0].updatedAt").value("2026-08-07T01:10:31Z"));

        verify(getRefundFailuresUseCase).get(
            USER_ID,
            java.util.Set.of(RefundStatus.FAILED, RefundStatus.DISCREPANT)
        );
    }

    @Test
    void getFailures_명시상태로_빈배열을_반환한다() throws Exception {
        when(getRefundFailuresUseCase.get(USER_ID, java.util.Set.of(RefundStatus.FAILED)))
            .thenReturn(List.of());

        mockMvc.perform(authenticated(get("/api/v1/platform-admin/refund-failures")
                .queryParam("status", "FAILED")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.refunds").isArray())
            .andExpect(jsonPath("$.data.refunds").isEmpty());

        verify(getRefundFailuresUseCase).get(USER_ID, java.util.Set.of(RefundStatus.FAILED));
    }

    @Test
    void getFailures_허용하지않은상태면_유스케이스를호출하지않고_입력오류를반환한다() throws Exception {
        mockMvc.perform(authenticated(get("/api/v1/platform-admin/refund-failures")
                .queryParam("status", "CLOSED")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(getRefundFailuresUseCase);
    }

    @Test
    void getFailures_권한이없으면_권한오류를반환한다() throws Exception {
        when(getRefundFailuresUseCase.get(eq(USER_ID), any())).thenThrow(
            new BusinessException(ErrorCode.FORBIDDEN)
        );

        mockMvc.perform(authenticated(get("/api/v1/platform-admin/refund-failures")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void getFailures_인증정보가없으면_유스케이스를호출하지않고_미인증오류를반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/platform-admin/refund-failures"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verifyNoInteractions(getRefundFailuresUseCase);
    }

    private RefundFailureListInfo refund() {
        return new RefundFailureListInfo(
            552L,
            903L,
            124L,
            12_000L,
            "KRW",
            RefundStatus.DISCREPANT,
            1,
            Instant.parse("2026-08-07T01:10:00Z"),
            Instant.parse("2026-08-07T01:10:31Z")
        );
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authenticated(
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request
    ) {
        return request.header(AUTHORIZATION, "Bearer " + AccessTokenTestFactory.issueForAuthenticatedRequest(jwtAccessTokenService, USER_ID));
    }
}
