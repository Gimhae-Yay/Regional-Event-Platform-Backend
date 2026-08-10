package io.regionevent.regioneventbackend.domain.coupon.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.regionevent.regioneventbackend.domain.coupon.dto.CreateCouponPolicyRequest;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicyStatus;
import io.regionevent.regioneventbackend.domain.coupon.service.CreateCouponPolicyResult;
import io.regionevent.regioneventbackend.domain.coupon.service.CreateCouponPolicyUseCase;
import io.regionevent.regioneventbackend.domain.coupon.service.PublishCouponPolicyResult;
import io.regionevent.regioneventbackend.domain.coupon.service.PublishCouponPolicyUseCase;
import io.regionevent.regioneventbackend.domain.coupon.service.UpdateCouponPolicyResult;
import io.regionevent.regioneventbackend.domain.coupon.service.UpdateCouponPolicyUseCase;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.config.SecurityConfig;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.error.GlobalExceptionHandler;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshTokenStore;

@WebMvcTest(CouponPolicyController.class)
@Import({
    SecurityConfig.class,
    RequestIdFilter.class,
    GlobalExceptionHandler.class
})
class CouponPolicyControllerIntegrationTest {

    private static final Long AUTHENTICATED_USER_ID = 100L;
    private static final Long CONTENT_ID = 200L;
    private static final String VALID_REQUEST = """
        {
          "contentId": "200",
          "name": "재방문 할인",
          "description": "방문 혜택",
          "issueSourceType": "VISIT",
          "discountAmount": 3000,
          "minimumPaymentAmount": 10000,
          "validDaysAfterIssue": 30,
          "issueStartsAt": "2026-08-01T00:00:00Z",
          "issueEndsAt": "2026-08-31T00:00:00Z",
          "totalIssueLimit": 1000
        }
        """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAccessTokenService jwtAccessTokenService;

    @MockitoBean
    private RefreshTokenStore refreshTokenStore;

    @MockitoBean
    private CreateCouponPolicyUseCase createCouponPolicyUseCase;

    @MockitoBean
    private PublishCouponPolicyUseCase publishCouponPolicyUseCase;

    @MockitoBean
    private UpdateCouponPolicyUseCase updateCouponPolicyUseCase;

    @Test
    void 쿠폰_정책_생성_유효하면_DRAFT_정책을_응답한다() throws Exception {
        when(createCouponPolicyUseCase.create(
            eq(AUTHENTICATED_USER_ID),
            eq(CONTENT_ID),
            any(CreateCouponPolicyRequest.class)
        )).thenReturn(result());

        mockMvc.perform(authenticated(post("/api/v1/operator/coupon-policies"))
                .contentType(APPLICATION_JSON)
                .content(VALID_REQUEST))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("쿠폰 정책 생성에 성공했습니다."))
            .andExpect(jsonPath("$.data.couponPolicyId").value("300"))
            .andExpect(jsonPath("$.data.contentId").value("200"))
            .andExpect(jsonPath("$.data.regionId").value("10"))
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andExpect(jsonPath("$.data.issueSourceType").value("VISIT"))
            .andExpect(jsonPath("$.data.createdAt").value("2026-08-08T00:00:00Z"));

        verify(createCouponPolicyUseCase).create(
            eq(AUTHENTICATED_USER_ID),
            eq(CONTENT_ID),
            any(CreateCouponPolicyRequest.class)
        );
    }

    @Test
    void 쿠폰_정책_생성_인증이_없으면_유스케이스를_호출하지_않는다() throws Exception {
        mockMvc.perform(post("/api/v1/operator/coupon-policies")
                .contentType(APPLICATION_JSON)
                .content(VALID_REQUEST))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verify(createCouponPolicyUseCase, never()).create(any(), any(), any());
    }

    @Test
    void 쿠폰_정책_생성_입력이_유효하지_않으면_입력오류를_응답한다() throws Exception {
        mockMvc.perform(authenticated(post("/api/v1/operator/coupon-policies"))
                .contentType(APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        mockMvc.perform(authenticated(post("/api/v1/operator/coupon-policies"))
                .contentType(APPLICATION_JSON)
                .content(VALID_REQUEST.replace("\"200\"", "\"0\"")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verify(createCouponPolicyUseCase, never()).create(any(), any(), any());
    }

    @Test
    void 쿠폰_정책_생성_필드타입이_다르면_타입오류를_응답한다() throws Exception {
        mockMvc.perform(authenticated(post("/api/v1/operator/coupon-policies"))
                .contentType(APPLICATION_JSON)
                .content(VALID_REQUEST.replace("\"discountAmount\": 3000", "\"discountAmount\": {}")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));

        verify(createCouponPolicyUseCase, never()).create(any(), any(), any());
    }

    @Test
    void 쿠폰_정책_생성_권한과_상태오류를_공통_오류로_응답한다() throws Exception {
        expectBusinessError(ErrorCode.FORBIDDEN, 403, "FORBIDDEN");
        expectBusinessError(ErrorCode.NOT_FOUND, 404, "NOT_FOUND");
        expectBusinessError(ErrorCode.COUPON_POLICY_CONFLICT, 409, "COUPON_POLICY_CONFLICT");
    }

    @Test
    void 쿠폰_정책_공개_유효하면_PUBLISHED_정책을_응답한다() throws Exception {
        when(publishCouponPolicyUseCase.publish(
            eq(AUTHENTICATED_USER_ID),
            eq(300L),
            eq("검토 완료 후 공개"),
            any()
        )).thenReturn(new PublishCouponPolicyResult(
            300L,
            CouponPolicyStatus.PUBLISHED,
            Instant.parse("2026-08-08T00:00:00Z")
        ));

        mockMvc.perform(authenticated(post("/api/v1/operator/coupon-policies/300/publish"))
                .contentType(APPLICATION_JSON)
                .content("{\"reason\":\"검토 완료 후 공개\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("쿠폰 정책 공개에 성공했습니다."))
            .andExpect(jsonPath("$.data.couponPolicyId").value("300"))
            .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
            .andExpect(jsonPath("$.data.publishedAt").value("2026-08-08T00:00:00Z"));
    }

    @Test
    void 쿠폰_정책_공개_대상이_없거나_권한과_상태가_맞지않으면_공통_오류를_응답한다() throws Exception {
        expectPublishBusinessError(ErrorCode.NOT_FOUND, 404, "NOT_FOUND");
        expectPublishBusinessError(ErrorCode.FORBIDDEN, 403, "FORBIDDEN");
        expectPublishBusinessError(ErrorCode.COUPON_POLICY_CONFLICT, 409, "COUPON_POLICY_CONFLICT");
    }

    @Test
    void 쿠폰_정책_수정_유효하면_수정된_DRAFT_정책을_응답한다() throws Exception {
        when(updateCouponPolicyUseCase.update(
            eq(AUTHENTICATED_USER_ID),
            eq(300L),
            any(),
            any()
        )).thenReturn(new UpdateCouponPolicyResult(
            300L,
            CouponPolicyStatus.DRAFT,
            "재방문 할인 수정",
            4_000L,
            12_000L,
            45,
            Instant.parse("2026-08-09T00:00:00Z")
        ));

        mockMvc.perform(authenticated(patch("/api/v1/operator/coupon-policies/300"))
                .contentType(APPLICATION_JSON)
                .content("{\"name\":\"재방문 할인 수정\",\"discountAmount\":4000,\"reason\":\"혜택 조정\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("쿠폰 정책 수정에 성공했습니다."))
            .andExpect(jsonPath("$.data.couponPolicyId").value("300"))
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andExpect(jsonPath("$.data.name").value("재방문 할인 수정"))
            .andExpect(jsonPath("$.data.updatedAt").value("2026-08-09T00:00:00Z"));
    }

    @Test
    void 쿠폰_정책_수정_권한과_상태오류를_공통_오류로_응답한다() throws Exception {
        expectUpdateBusinessError(ErrorCode.FORBIDDEN, 403, "FORBIDDEN");
        expectUpdateBusinessError(ErrorCode.COUPON_POLICY_CONFLICT, 409, "COUPON_POLICY_CONFLICT");
    }

    @Test
    void 쿠폰_정책_수정_식별자_형식이_잘못되면_타입오류를_응답한다() throws Exception {
        mockMvc.perform(authenticated(patch("/api/v1/operator/coupon-policies/invalid"))
                .contentType(APPLICATION_JSON)
                .content("{\"name\":\"재방문 할인 수정\",\"reason\":\"혜택 조정\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));

        verify(updateCouponPolicyUseCase, never()).update(any(), any(), any(), any());
    }

    private void expectUpdateBusinessError(ErrorCode errorCode, int statusCode, String code) throws Exception {
        when(updateCouponPolicyUseCase.update(
            eq(AUTHENTICATED_USER_ID),
            eq(300L),
            any(),
            any()
        )).thenThrow(new BusinessException(errorCode));

        mockMvc.perform(authenticated(patch("/api/v1/operator/coupon-policies/300"))
                .contentType(APPLICATION_JSON)
                .content("{\"name\":\"재방문 할인 수정\",\"reason\":\"혜택 조정\"}"))
            .andExpect(status().is(statusCode))
            .andExpect(jsonPath("$.code").value(code));
    }

    private void expectPublishBusinessError(
        ErrorCode errorCode,
        int statusCode,
        String code
    ) throws Exception {
        when(publishCouponPolicyUseCase.publish(
            eq(AUTHENTICATED_USER_ID),
            eq(300L),
            eq("검토 완료 후 공개"),
            any()
        )).thenThrow(new BusinessException(errorCode));

        mockMvc.perform(authenticated(post("/api/v1/operator/coupon-policies/300/publish"))
                .contentType(APPLICATION_JSON)
                .content("{\"reason\":\"검토 완료 후 공개\"}"))
            .andExpect(status().is(statusCode))
            .andExpect(jsonPath("$.code").value(code))
            .andExpect(jsonPath("$.message").value(errorCode.message()));
    }

    private void expectBusinessError(ErrorCode errorCode, int statusCode, String code) throws Exception {
        when(createCouponPolicyUseCase.create(
            eq(AUTHENTICATED_USER_ID),
            eq(CONTENT_ID),
            any(CreateCouponPolicyRequest.class)
        )).thenThrow(new BusinessException(errorCode));

        mockMvc.perform(authenticated(post("/api/v1/operator/coupon-policies"))
                .contentType(APPLICATION_JSON)
                .content(VALID_REQUEST))
            .andExpect(status().is(statusCode))
            .andExpect(jsonPath("$.code").value(code));
    }

    private CreateCouponPolicyResult result() {
        return new CreateCouponPolicyResult(
            300L,
            CONTENT_ID,
            10L,
            "재방문 할인",
            CouponPolicyStatus.DRAFT,
            CouponIssuanceType.VISIT,
            3_000L,
            10_000L,
            30,
            Instant.parse("2026-08-01T00:00:00Z"),
            Instant.parse("2026-08-31T00:00:00Z"),
            1_000L,
            Instant.parse("2026-08-08T00:00:00Z")
        );
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authenticated(
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder requestBuilder
    ) {
        return requestBuilder.header(AUTHORIZATION, "Bearer " + jwtAccessTokenService.issue(AUTHENTICATED_USER_ID));
    }
}
