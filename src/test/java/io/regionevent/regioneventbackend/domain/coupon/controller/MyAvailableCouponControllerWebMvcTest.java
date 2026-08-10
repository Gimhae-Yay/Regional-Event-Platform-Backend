package io.regionevent.regioneventbackend.domain.coupon.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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

import io.regionevent.regioneventbackend.domain.coupon.service.GetMyAvailableCouponsResult;
import io.regionevent.regioneventbackend.domain.coupon.service.GetMyAvailableCouponsUseCase;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.config.SecurityConfig;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.error.GlobalExceptionHandler;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshTokenStore;

@WebMvcTest(MyAvailableCouponController.class)
@Import({
    SecurityConfig.class,
    RequestIdFilter.class,
    GlobalExceptionHandler.class
})
class MyAvailableCouponControllerWebMvcTest {

    private static final Long USER_ID = 100L;
    private static final Long HOLD_ID = 200L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAccessTokenService jwtAccessTokenService;

    @MockitoBean
    private RefreshTokenStore refreshTokenStore;

    @MockitoBean
    private GetMyAvailableCouponsUseCase getMyAvailableCouponsUseCase;

    @Test
    void getMyAvailableCoupons_쿠폰이_없으면_빈_목록을_반환한다() throws Exception {
        when(getMyAvailableCouponsUseCase.findAll(USER_ID, HOLD_ID)).thenReturn(
            new GetMyAvailableCouponsResult(HOLD_ID, Instant.parse("2026-08-10T00:00:00Z"), List.of())
        );

        mockMvc.perform(authenticated(get("/api/v1/me/coupons/available").param("holdId", "200")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.holdId").value("200"))
            .andExpect(jsonPath("$.data.availableCoupons").isArray())
            .andExpect(jsonPath("$.data.availableCoupons").isEmpty());
    }

    @Test
    void getMyAvailableCoupons_타인_홀드면_권한_오류를_반환한다() throws Exception {
        when(getMyAvailableCouponsUseCase.findAll(USER_ID, HOLD_ID))
            .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        mockMvc.perform(authenticated(get("/api/v1/me/coupons/available").param("holdId", "200")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void getMyAvailableCoupons_홀드가_없으면_대상_없음_오류를_반환한다() throws Exception {
        when(getMyAvailableCouponsUseCase.findAll(USER_ID, HOLD_ID))
            .thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

        mockMvc.perform(authenticated(get("/api/v1/me/coupons/available").param("holdId", "200")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void getMyAvailableCoupons_홀드_식별자가_숫자가_아니면_형식_오류를_반환한다() throws Exception {
        mockMvc.perform(authenticated(get("/api/v1/me/coupons/available").param("holdId", "invalid")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));

        verify(getMyAvailableCouponsUseCase, never()).findAll(anyLong(), anyLong());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authenticated(
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder requestBuilder
    ) {
        return requestBuilder.header(AUTHORIZATION, "Bearer " + jwtAccessTokenService.issue(USER_ID));
    }
}
