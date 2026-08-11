package io.regionevent.regioneventbackend.domain.mission.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import io.regionevent.regioneventbackend.domain.mission.service.ClaimMissionRewardResult;
import io.regionevent.regioneventbackend.domain.mission.service.ClaimMissionRewardUseCase;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.config.SecurityConfig;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.error.GlobalExceptionHandler;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshTokenStore;

@WebMvcTest(ClaimMissionRewardController.class)
@Import({SecurityConfig.class, RequestIdFilter.class, GlobalExceptionHandler.class})
class ClaimMissionRewardControllerWebMvcTest {

    private static final Long VISITOR_ID = 100L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAccessTokenService jwtAccessTokenService;

    @MockitoBean
    private ClaimMissionRewardUseCase claimMissionRewardUseCase;

    @MockitoBean
    private RefreshTokenStore refreshTokenStore;

    @Test
    void claim_정상요청이면정확한Created응답을반환한다() throws Exception {
        when(claimMissionRewardUseCase.claim(any(), any(), any())).thenReturn(new ClaimMissionRewardResult(
            9001L,
            701L,
            8001L,
            301L,
            Instant.parse("2026-08-11T00:00:00Z")
        ));

        mockMvc.perform(authenticated(post("/api/v1/me/mission-participations/701/rewards/claim")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.statusCode").value(201))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("미션 보상 수령에 성공했습니다."))
            .andExpect(jsonPath("$.data.missionRewardClaimId").value("9001"))
            .andExpect(jsonPath("$.data.participationId").value("701"))
            .andExpect(jsonPath("$.data.couponId").value("8001"))
            .andExpect(jsonPath("$.data.couponPolicyId").value("301"))
            .andExpect(jsonPath("$.data.claimedAt").value("2026-08-11T00:00:00Z"));

        verify(claimMissionRewardUseCase).claim(any(), any(), any());
    }

    @Test
    void claim_잘못된참여ID이면UseCase을호출하지않는다() throws Exception {
        mockMvc.perform(authenticated(post("/api/v1/me/mission-participations/01/rewards/claim")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(authenticated(post("/api/v1/me/mission-participations/not-a-number/rewards/claim")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));

        verifyNoInteractions(claimMissionRewardUseCase);
    }

    @Test
    void claim_미인증이면Unauthenticated를반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/me/mission-participations/701/rewards/claim"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void claim_업무오류이면공개오류계약을반환한다() throws Exception {
        assertBusinessError(701L, ErrorCode.FORBIDDEN, 403);
        assertBusinessError(702L, ErrorCode.NOT_FOUND, 404);
        assertBusinessError(703L, ErrorCode.MISSION_REWARD_CLAIM_CONFLICT, 409);
    }

    private void assertBusinessError(Long participationId, ErrorCode errorCode, int statusCode) throws Exception {
        doThrow(new BusinessException(errorCode))
            .when(claimMissionRewardUseCase).claim(any(), any(), any());

        mockMvc.perform(authenticated(post(
                "/api/v1/me/mission-participations/{participationId}/rewards/claim",
                participationId
            )))
            .andExpect(status().is(statusCode))
            .andExpect(jsonPath("$.code").value(errorCode.code()));
    }

    private MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder requestBuilder) {
        return requestBuilder.header("Authorization", "Bearer " + jwtAccessTokenService.issue(VISITOR_ID));
    }
}
