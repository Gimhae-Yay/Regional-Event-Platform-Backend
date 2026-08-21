package io.regionevent.regioneventbackend.domain.coupon.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

import io.regionevent.regioneventbackend.domain.coupon.entity.CouponRedemptionStatus;
import io.regionevent.regioneventbackend.domain.coupon.service.GetMyCouponUsageHistoryResult;
import io.regionevent.regioneventbackend.domain.coupon.service.GetMyCouponUsageHistoryUseCase;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.config.SecurityConfig;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.error.GlobalExceptionHandler;
import io.regionevent.regioneventbackend.global.security.access.AccessTokenTestFactory;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;

@WebMvcTest(MyCouponUsageHistoryController.class)
@Import({SecurityConfig.class, RequestIdFilter.class, GlobalExceptionHandler.class})
class MyCouponUsageHistoryControllerIntegrationTest {

    private static final Long AUTHENTICATED_USER_ID = 100L;
    private static final Long COUPON_ID = 200L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAccessTokenService jwtAccessTokenService;

    @MockitoBean
    private GetMyCouponUsageHistoryUseCase getMyCouponUsageHistoryUseCase;

    @Test
    void 내_쿠폰_사용_이력이_없으면_빈_목록을_반환한다() throws Exception {
        when(getMyCouponUsageHistoryUseCase.find(AUTHENTICATED_USER_ID, COUPON_ID))
            .thenReturn(new GetMyCouponUsageHistoryResult(COUPON_ID, List.of()));

        mockMvc.perform(authenticated(get("/api/v1/me/coupons/200/usage-history")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.couponId").value("200"))
            .andExpect(jsonPath("$.data.usageHistory").isArray())
            .andExpect(jsonPath("$.data.usageHistory").isEmpty());
    }

    @Test
    void 내_쿠폰_사용_이력을_계약된_형식으로_반환한다() throws Exception {
        when(getMyCouponUsageHistoryUseCase.find(AUTHENTICATED_USER_ID, COUPON_ID))
            .thenReturn(new GetMyCouponUsageHistoryResult(
                COUPON_ID,
                List.of(new GetMyCouponUsageHistoryResult.UsageHistory(
                    300L,
                    400L,
                    500L,
                    CouponRedemptionStatus.REVERSED,
                    3_000L,
                    Instant.parse("2026-08-10T00:00:00Z"),
                    Instant.parse("2026-08-11T00:00:00Z")
                ))
            ));

        mockMvc.perform(authenticated(get("/api/v1/me/coupons/200/usage-history")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.usageHistory[0].couponRedemptionId").value("300"))
            .andExpect(jsonPath("$.data.usageHistory[0].reservationId").value("400"))
            .andExpect(jsonPath("$.data.usageHistory[0].priceSnapshotId").value("500"))
            .andExpect(jsonPath("$.data.usageHistory[0].status").value("REVERSED"))
            .andExpect(jsonPath("$.data.usageHistory[0].discountAmount").value(3_000))
            .andExpect(jsonPath("$.data.usageHistory[0].confirmedAt").value("2026-08-10T00:00:00Z"))
            .andExpect(jsonPath("$.data.usageHistory[0].reversedAt").value("2026-08-11T00:00:00Z"))
            .andExpect(jsonPath("$.data.usageHistory[0].reversalReason").doesNotExist());
    }

    @Test
    void 존재하지_않는_쿠폰이면_NOT_FOUND를_반환한다() throws Exception {
        when(getMyCouponUsageHistoryUseCase.find(AUTHENTICATED_USER_ID, COUPON_ID))
            .thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

        mockMvc.perform(authenticated(get("/api/v1/me/coupons/200/usage-history")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void 미인증_요청은_거부한다() throws Exception {
        mockMvc.perform(get("/api/v1/me/coupons/200/usage-history"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verify(getMyCouponUsageHistoryUseCase, never()).find(any(), any());
    }

    @Test
    void 잘못된_쿠폰_식별자는_거부한다() throws Exception {
        mockMvc.perform(authenticated(get("/api/v1/me/coupons/0/usage-history")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(authenticated(get("/api/v1/me/coupons/not-a-number/usage-history")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));

        verify(getMyCouponUsageHistoryUseCase, never()).find(eq(AUTHENTICATED_USER_ID), any());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authenticated(
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder requestBuilder
    ) {
        return requestBuilder.header(
            AUTHORIZATION,
            "Bearer " + AccessTokenTestFactory.issueForAuthenticatedRequest(jwtAccessTokenService, AUTHENTICATED_USER_ID)
        );
    }
}
