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

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicyStatus;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponStatus;
import io.regionevent.regioneventbackend.domain.coupon.service.GetMyCouponResult;
import io.regionevent.regioneventbackend.domain.coupon.service.GetMyCouponUseCase;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.config.SecurityConfig;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.error.GlobalExceptionHandler;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshTokenStore;

@WebMvcTest(MyCouponDetailController.class)
@Import({SecurityConfig.class, RequestIdFilter.class, GlobalExceptionHandler.class})
class MyCouponDetailControllerIntegrationTest {

    private static final Long AUTHENTICATED_USER_ID = 100L;
    private static final Long COUPON_ID = 200L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAccessTokenService jwtAccessTokenService;

    @MockitoBean
    private RefreshTokenStore refreshTokenStore;

    @MockitoBean
    private GetMyCouponUseCase getMyCouponUseCase;

    @Test
    void 내_쿠폰_상세_조회에_성공하면_계약한_응답을_반환한다() throws Exception {
        when(getMyCouponUseCase.find(AUTHENTICATED_USER_ID, COUPON_ID)).thenReturn(result());

        mockMvc.perform(authenticated(get("/api/v1/me/coupons/200")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.coupon.couponId").value("200"))
            .andExpect(jsonPath("$.data.coupon.couponPolicyId").value("300"))
            .andExpect(jsonPath("$.data.coupon.policyName").value("방문 할인"))
            .andExpect(jsonPath("$.data.coupon.issueSourceType").value("VISIT"))
            .andExpect(jsonPath("$.data.coupon.sourceId").value("400"))
            .andExpect(jsonPath("$.data.coupon.status").value("AVAILABLE"))
            .andExpect(jsonPath("$.data.policy.contentId").value("500"))
            .andExpect(jsonPath("$.data.policy.regionId").value("600"))
            .andExpect(jsonPath("$.data.policy.status").value("PUBLISHED"))
            .andExpect(jsonPath("$.data.policy.validDaysAfterIssue").value(30));
    }

    @Test
    void 내_쿠폰_상세_조회는_대상이_없으면_NOT_FOUND를_반환한다() throws Exception {
        when(getMyCouponUseCase.find(AUTHENTICATED_USER_ID, COUPON_ID))
            .thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

        mockMvc.perform(authenticated(get("/api/v1/me/coupons/200")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void 내_쿠폰_상세_조회는_타인_쿠폰이면_FORBIDDEN을_반환한다() throws Exception {
        when(getMyCouponUseCase.find(AUTHENTICATED_USER_ID, COUPON_ID))
            .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        mockMvc.perform(authenticated(get("/api/v1/me/coupons/200")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void 내_쿠폰_상세_조회는_미인증_요청을_거부한다() throws Exception {
        mockMvc.perform(get("/api/v1/me/coupons/200"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verify(getMyCouponUseCase, never()).find(any(), any());
    }

    @Test
    void 내_쿠폰_상세_조회는_잘못된_식별자를_거부한다() throws Exception {
        mockMvc.perform(authenticated(get("/api/v1/me/coupons/0")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(authenticated(get("/api/v1/me/coupons/not-a-number")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));

        verify(getMyCouponUseCase, never()).find(eq(AUTHENTICATED_USER_ID), any());
    }

    private GetMyCouponResult result() {
        return new GetMyCouponResult(
            COUPON_ID,
            300L,
            "방문 할인",
            CouponIssuanceType.VISIT,
            400L,
            CouponStatus.AVAILABLE,
            3_000L,
            10_000L,
            Instant.parse("2026-08-10T00:00:00Z"),
            Instant.parse("2026-09-09T00:00:00Z"),
            500L,
            600L,
            CouponPolicyStatus.PUBLISHED,
            30
        );
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authenticated(
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder requestBuilder
    ) {
        return requestBuilder.header(AUTHORIZATION, "Bearer " + jwtAccessTokenService.issue(AUTHENTICATED_USER_ID));
    }
}
