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

import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponStatus;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponSummary;
import io.regionevent.regioneventbackend.domain.coupon.service.GetMyCouponsUseCase;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.config.SecurityConfig;
import io.regionevent.regioneventbackend.global.error.GlobalExceptionHandler;
import io.regionevent.regioneventbackend.global.security.access.AccessTokenTestFactory;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshTokenStore;

@WebMvcTest(MyCouponController.class)
@Import({
    SecurityConfig.class,
    RequestIdFilter.class,
    GlobalExceptionHandler.class
})
class MyCouponControllerWebMvcTest {

    private static final Long USER_ID = 100L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAccessTokenService jwtAccessTokenService;

    @MockitoBean
    private RefreshTokenStore refreshTokenStore;

    @MockitoBean
    private GetMyCouponsUseCase getMyCouponsUseCase;

    @Test
    void getMyCoupons_쿠폰이_없으면_빈목록을_반환한다() throws Exception {
        when(getMyCouponsUseCase.findAll(USER_ID, null)).thenReturn(List.of());

        mockMvc.perform(authenticated(get("/api/v1/me/coupons")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("내 쿠폰 목록 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.coupons").isArray())
            .andExpect(jsonPath("$.data.coupons").isEmpty());
    }

    @Test
    void getMyCoupons_상태필터가_있으면_해당상태_쿠폰을_반환한다() throws Exception {
        when(getMyCouponsUseCase.findAll(USER_ID, CouponStatus.AVAILABLE)).thenReturn(List.of(summary()));

        mockMvc.perform(authenticated(get("/api/v1/me/coupons").param("status", "AVAILABLE")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.coupons[0].couponId").value("300"))
            .andExpect(jsonPath("$.data.coupons[0].couponPolicyId").value("200"))
            .andExpect(jsonPath("$.data.coupons[0].contentId").value("101"))
            .andExpect(jsonPath("$.data.coupons[0].regionId").value("10"))
            .andExpect(jsonPath("$.data.coupons[0].status").value("AVAILABLE"));

        verify(getMyCouponsUseCase).findAll(USER_ID, CouponStatus.AVAILABLE);
    }

    @Test
    void getMyCoupons_잘못된_상태필터면_입력오류를_반환한다() throws Exception {
        mockMvc.perform(authenticated(get("/api/v1/me/coupons").param("status", "UNKNOWN")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verify(getMyCouponsUseCase, never()).findAll(any(), any());
    }

    @Test
    void getMyCoupons_인증이_없으면_미인증오류를_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/me/coupons"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verify(getMyCouponsUseCase, never()).findAll(any(), any());
    }

    private CouponSummary summary() {
        return new CouponSummary(
            300L,
            200L,
            101L,
            10L,
            "재방문 할인",
            CouponIssuanceType.VISIT,
            CouponStatus.AVAILABLE,
            3_000L,
            10_000L,
            Instant.parse("2026-08-01T00:00:00Z"),
            Instant.parse("2026-08-31T00:00:00Z")
        );
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authenticated(
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder requestBuilder
    ) {
        return requestBuilder.header(AUTHORIZATION, "Bearer " + AccessTokenTestFactory.issueForAuthenticatedRequest(jwtAccessTokenService, USER_ID));
    }
}
