package io.regionevent.regioneventbackend.domain.coupon.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicyStatus;
import io.regionevent.regioneventbackend.domain.coupon.service.EndCouponPolicyResult;
import io.regionevent.regioneventbackend.domain.coupon.service.EndCouponPolicyUseCase;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.config.SecurityConfig;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.error.GlobalExceptionHandler;
import io.regionevent.regioneventbackend.global.security.access.AccessTokenTestFactory;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshTokenStore;

@WebMvcTest(EndCouponPolicyController.class)
@Import({SecurityConfig.class, RequestIdFilter.class, GlobalExceptionHandler.class})
class EndCouponPolicyControllerWebMvcTest {

    private static final long OPERATOR_USER_ID = 100L;
    private static final String END_PATH = "/api/v1/operator/coupon-policies/101/end";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAccessTokenService jwtAccessTokenService;

    @MockitoBean
    private EndCouponPolicyUseCase endCouponPolicyUseCase;

    @MockitoBean
    private RefreshTokenStore refreshTokenStore;

    @Test
    void end_유효한_요청이면_종료_응답을_반환한다() throws Exception {
        when(endCouponPolicyUseCase.end(eq(OPERATOR_USER_ID), eq(101L), eq("프로모션 종료"), any()))
            .thenReturn(new EndCouponPolicyResult(
                101L,
                CouponPolicyStatus.ENDED,
                Instant.parse("2026-08-10T00:00:00Z")
            ));

        mockMvc.perform(authenticated(post(END_PATH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("쿠폰 정책 종료에 성공했습니다."))
            .andExpect(jsonPath("$.data.couponPolicyId").value("101"))
            .andExpect(jsonPath("$.data.status").value("ENDED"))
            .andExpect(jsonPath("$.data.endedAt").value("2026-08-10T00:00:00Z"));

        verify(endCouponPolicyUseCase).end(eq(OPERATOR_USER_ID), eq(101L), eq("프로모션 종료"), any());
    }

    @Test
    void end_참조_충돌과_대상_없음과_권한_오류를_공통_오류로_반환한다() throws Exception {
        expectBusinessError(ErrorCode.COUPON_POLICY_REFERENCED, 409, "COUPON_POLICY_REFERENCED");
        expectBusinessError(ErrorCode.NOT_FOUND, 404, "NOT_FOUND");
        expectBusinessError(ErrorCode.FORBIDDEN, 403, "FORBIDDEN");
    }

    @Test
    void end_빈_사유와_범위를_벗어난_식별자는_입력_오류를_반환하고_유스케이스를_호출하지_않는다() throws Exception {
        mockMvc.perform(authenticated(post(END_PATH))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"   \"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        mockMvc.perform(authenticated(post("/api/v1/operator/coupon-policies/9223372036854775808/end"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));

        verifyNoInteractions(endCouponPolicyUseCase);
    }

    @Test
    void end_인증_정보가_없으면_미인증_오류를_반환한다() throws Exception {
        mockMvc.perform(post(END_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verifyNoInteractions(endCouponPolicyUseCase);
    }

    private void expectBusinessError(
        ErrorCode errorCode,
        int statusCode,
        String code
    ) throws Exception {
        when(endCouponPolicyUseCase.end(eq(OPERATOR_USER_ID), eq(101L), eq("프로모션 종료"), any()))
            .thenThrow(new BusinessException(errorCode));

        mockMvc.perform(authenticated(post(END_PATH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
            .andExpect(status().is(statusCode))
            .andExpect(jsonPath("$.code").value(code))
            .andExpect(jsonPath("$.message").value(errorCode.message()));
    }

    private String validRequest() {
        return "{\"reason\":\"프로모션 종료\"}";
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authenticated(
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder requestBuilder
    ) {
        return requestBuilder.header(AUTHORIZATION, "Bearer " + AccessTokenTestFactory.issueForAuthenticatedRequest(jwtAccessTokenService, OPERATOR_USER_ID));
    }
}
