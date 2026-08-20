package io.regionevent.regioneventbackend.domain.stampbook.controller;

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

import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicyStatus;
import io.regionevent.regioneventbackend.domain.stampbook.dto.OperatorStampbookDetailResponse;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookStatus;
import io.regionevent.regioneventbackend.domain.stampbook.service.GetOperatorStampbookDetailUseCase;
import io.regionevent.regioneventbackend.global.config.SecurityConfig;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.error.GlobalExceptionHandler;
import io.regionevent.regioneventbackend.global.security.access.AccessTokenTestFactory;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;

@WebMvcTest(OperatorStampbookDetailController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class OperatorStampbookDetailControllerWebMvcTest {

    private static final long OPERATOR_ID = 100L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAccessTokenService jwtAccessTokenService;

    @MockitoBean
    private GetOperatorStampbookDetailUseCase getOperatorStampbookDetailUseCase;

    @Test
    void 상세_조회는_명세_응답을_반환한다() throws Exception {
        when(getOperatorStampbookDetailUseCase.get(OPERATOR_ID, 701L)).thenReturn(
            new OperatorStampbookDetailResponse(
                "701",
                "김해 가야 문화 완주",
                "11",
                StampbookStatus.ENDED,
                List.of(new OperatorStampbookDetailResponse.TargetContentResponse(
                    "101",
                    "11",
                    "가야문화 체험",
                    ContentStatus.PUBLISHED
                )),
                new OperatorStampbookDetailResponse.RewardCouponPolicyResponse(
                    "501",
                    "11",
                    CouponIssuanceType.STAMPBOOK_COMPLETION,
                    CouponPolicyStatus.PUBLISHED
                ),
                Instant.parse("2026-08-01T01:00:00Z"),
                Instant.parse("2026-08-20T01:00:00Z")
            )
        );

        mockMvc.perform(authenticated(get("/api/v1/operator/stampbooks/701")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("운영자 스탬프북 상세 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.stampbookId").value("701"))
            .andExpect(jsonPath("$.data.title").value("김해 가야 문화 완주"))
            .andExpect(jsonPath("$.data.regionId").value("11"))
            .andExpect(jsonPath("$.data.status").value("ENDED"))
            .andExpect(jsonPath("$.data.targetContents[0].contentId").value("101"))
            .andExpect(jsonPath("$.data.targetContents[0].regionId").value("11"))
            .andExpect(jsonPath("$.data.targetContents[0].title").value("가야문화 체험"))
            .andExpect(jsonPath("$.data.targetContents[0].status").value("PUBLISHED"))
            .andExpect(jsonPath("$.data.rewardCouponPolicy.couponPolicyId").value("501"))
            .andExpect(jsonPath("$.data.rewardCouponPolicy.regionId").value("11"))
            .andExpect(jsonPath("$.data.rewardCouponPolicy.issuanceType").value("STAMPBOOK_COMPLETION"))
            .andExpect(jsonPath("$.data.rewardCouponPolicy.status").value("PUBLISHED"))
            .andExpect(jsonPath("$.data.publishedAt").value("2026-08-01T01:00:00Z"))
            .andExpect(jsonPath("$.data.endedAt").value("2026-08-20T01:00:00Z"));

        verify(getOperatorStampbookDetailUseCase).get(OPERATOR_ID, 701L);
    }

    @Test
    void 유효하지_않은_식별자는_유스케이스를_호출하지_않는다() throws Exception {
        mockMvc.perform(authenticated(get("/api/v1/operator/stampbooks/01")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(authenticated(get("/api/v1/operator/stampbooks/not-a-number")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));
        mockMvc.perform(authenticated(get("/api/v1/operator/stampbooks/9223372036854775808")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(getOperatorStampbookDetailUseCase);
    }

    @Test
    void 인증_및_조회_오류를_계약대로_반환한다() throws Exception {
        when(getOperatorStampbookDetailUseCase.get(OPERATOR_ID, 701L))
            .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));
        when(getOperatorStampbookDetailUseCase.get(OPERATOR_ID, 702L))
            .thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

        mockMvc.perform(get("/api/v1/operator/stampbooks/701"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(authenticated(get("/api/v1/operator/stampbooks/701")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(authenticated(get("/api/v1/operator/stampbooks/702")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authenticated(
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder requestBuilder
    ) {
        return requestBuilder.header(
            AUTHORIZATION,
            "Bearer " + AccessTokenTestFactory.issueForAuthenticatedRequest(
                jwtAccessTokenService,
                OPERATOR_ID
            )
        );
    }
}
