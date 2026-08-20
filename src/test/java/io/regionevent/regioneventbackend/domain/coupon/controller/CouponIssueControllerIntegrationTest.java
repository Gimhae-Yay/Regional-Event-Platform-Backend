package io.regionevent.regioneventbackend.domain.coupon.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponStatus;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponIssueResult;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponIssueUseCase;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.config.SecurityConfig;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.error.GlobalExceptionHandler;
import io.regionevent.regioneventbackend.global.security.access.AccessTokenTestFactory;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;

@WebMvcTest(CouponIssueController.class)
@Import({SecurityConfig.class, RequestIdFilter.class, GlobalExceptionHandler.class})
class CouponIssueControllerIntegrationTest {

    private static final Long AUTHENTICATED_USER_ID = 100L;
    private static final Long COUPON_POLICY_ID = 200L;
    private static final String VALID_REQUEST = """
        {"issueSourceType":"VISIT","sourceId":"300"}
        """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAccessTokenService jwtAccessTokenService;

    @MockitoBean
    private CouponIssueUseCase couponIssueUseCase;

    @Test
    void 쿠폰_발급이_성공하면_생성_응답을_반환한다() throws Exception {
        when(couponIssueUseCase.issue(
            eq(AUTHENTICATED_USER_ID),
            eq("200"),
            eq("VISIT"),
            eq("300"),
            any()
        )).thenReturn(result(false));

        mockMvc.perform(authenticated(post("/api/v1/coupon-policies/200/coupons"))
                .contentType(APPLICATION_JSON)
                .content(VALID_REQUEST))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.couponId").value("400"))
            .andExpect(jsonPath("$.data.status").value("AVAILABLE"))
            .andExpect(jsonPath("$.data.duplicate").value(false));

        verify(couponIssueUseCase).issue(
            eq(AUTHENTICATED_USER_ID),
            eq("200"),
            eq("VISIT"),
            eq("300"),
            any()
        );
    }

    @Test
    void 쿠폰_발급은_인증되지_않으면_거부한다() throws Exception {
        mockMvc.perform(post("/api/v1/coupon-policies/200/coupons")
                .contentType(APPLICATION_JSON)
                .content(VALID_REQUEST))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verify(couponIssueUseCase, never()).issue(any(), any(), any(), any(), any());
    }

    @Test
    void 쿠폰_발급은_지원하지_않는_발급_경로를_거부한다() throws Exception {
        when(couponIssueUseCase.issue(
            eq(AUTHENTICATED_USER_ID), eq("200"), eq("MISSION_REWARD"), eq("300"), any()
        )).thenThrow(new BusinessException(ErrorCode.INVALID_INPUT));

        mockMvc.perform(authenticated(post("/api/v1/coupon-policies/200/coupons"))
                .contentType(APPLICATION_JSON)
                .content("{\"issueSourceType\":\"MISSION_REWARD\",\"sourceId\":\"300\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verify(couponIssueUseCase).issue(
            eq(AUTHENTICATED_USER_ID), eq("200"), eq("MISSION_REWARD"), eq("300"), any()
        );
    }

    @Test
    void 쿠폰_발급은_문자열_이외의_요청_필드를_거부한다() throws Exception {
        mockMvc.perform(authenticated(post("/api/v1/coupon-policies/200/coupons"))
                .contentType(APPLICATION_JSON)
                .content("{\"issueSourceType\":0,\"sourceId\":300}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));

        verify(couponIssueUseCase, never()).issue(any(), any(), any(), any(), any());
    }

    @Test
    void 쿠폰_발급은_지원하지_않는_문자열_발급_경로를_거부한다() throws Exception {
        for (String issueSourceType : new String[]{"0", "OTHER"}) {
            when(couponIssueUseCase.issue(
                eq(AUTHENTICATED_USER_ID), eq("200"), eq(issueSourceType), eq("300"), any()
            )).thenThrow(new BusinessException(ErrorCode.INVALID_INPUT));
            mockMvc.perform(authenticated(post("/api/v1/coupon-policies/200/coupons"))
                    .contentType(APPLICATION_JSON)
                    .content("{\"issueSourceType\":\"" + issueSourceType + "\",\"sourceId\":\"300\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }

        verify(couponIssueUseCase).issue(
            eq(AUTHENTICATED_USER_ID), eq("200"), eq("0"), eq("300"), any()
        );
        verify(couponIssueUseCase).issue(
            eq(AUTHENTICATED_USER_ID), eq("200"), eq("OTHER"), eq("300"), any()
        );
    }

    @Test
    void 쿠폰_발급은_업무_오류를_계약한_상태로_반환한다() throws Exception {
        when(couponIssueUseCase.issue(
            eq(AUTHENTICATED_USER_ID),
            eq("200"),
            eq("VISIT"),
            eq("300"),
            any()
        )).thenThrow(new BusinessException(ErrorCode.COUPON_POLICY_NOT_PUBLISHED));

        mockMvc.perform(authenticated(post("/api/v1/coupon-policies/200/coupons"))
                .contentType(APPLICATION_JSON)
                .content(VALID_REQUEST))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("COUPON_POLICY_NOT_PUBLISHED"));
    }

    private CouponIssueResult result(boolean duplicate) {
        return new CouponIssueResult(
            400L,
            COUPON_POLICY_ID,
            10L,
            20L,
            "방문 할인",
            CouponIssuanceType.VISIT,
            CouponStatus.AVAILABLE,
            3_000L,
            10_000L,
            Instant.parse("2026-08-09T00:00:00Z"),
            Instant.parse("2026-09-08T00:00:00Z"),
            duplicate
        );
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authenticated(
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder requestBuilder
    ) {
        return requestBuilder.header(AUTHORIZATION, "Bearer " + AccessTokenTestFactory.issueForAuthenticatedRequest(jwtAccessTokenService, AUTHENTICATED_USER_ID));
    }
}
