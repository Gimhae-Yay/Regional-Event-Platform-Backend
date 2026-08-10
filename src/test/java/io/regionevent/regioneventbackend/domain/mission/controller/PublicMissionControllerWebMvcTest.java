package io.regionevent.regioneventbackend.domain.mission.controller;

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

import io.regionevent.regioneventbackend.domain.mission.entity.MissionConditionType;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionParticipationStatus;
import io.regionevent.regioneventbackend.domain.mission.service.GetPublicMissionUseCase;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.config.SecurityConfig;
import io.regionevent.regioneventbackend.global.error.GlobalExceptionHandler;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshTokenStore;

@WebMvcTest(PublicMissionController.class)
@Import({SecurityConfig.class, RequestIdFilter.class, GlobalExceptionHandler.class})
class PublicMissionControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAccessTokenService jwtAccessTokenService;

    @MockitoBean
    private GetPublicMissionUseCase getPublicMissionUseCase;

    @MockitoBean
    private RefreshTokenStore refreshTokenStore;

    @Test
    void getPublicMission_anonymous_returnsContractResponse() throws Exception {
        io.regionevent.regioneventbackend.domain.mission.entity.Mission mission = mission();
        when(getPublicMissionUseCase.get(701L, null))
            .thenReturn(new io.regionevent.regioneventbackend.domain.mission.service.PublicMissionDetailResult(mission, null));

        mockMvc.perform(get("/api/v1/missions/701"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("공개 미션 상세 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.missionId").value("701"))
            .andExpect(jsonPath("$.data.requiredVisitCount").isEmpty())
            .andExpect(jsonPath("$.data.targetContents[0].contentId").value("101"))
            .andExpect(jsonPath("$.data.endsAt").value("2026-09-30T23:59:59+09:00"))
            .andExpect(jsonPath("$.data.participation").isEmpty());

        verify(getPublicMissionUseCase).get(701L, null);
    }

    @Test
    void getPublicMission_authenticated_returnsParticipation() throws Exception {
        String accessToken = jwtAccessTokenService.issue(100L);
        io.regionevent.regioneventbackend.domain.mission.entity.Mission mission = mission();
        io.regionevent.regioneventbackend.domain.mission.service.MissionParticipationSummary participation =
            new io.regionevent.regioneventbackend.domain.mission.service.MissionParticipationSummary(
                9001L, 701L, MissionParticipationStatus.IN_PROGRESS, 1, 3, false, Instant.EPOCH, null
            );
        when(getPublicMissionUseCase.get(701L, 100L))
            .thenReturn(new io.regionevent.regioneventbackend.domain.mission.service.PublicMissionDetailResult(
                mission,
                participation
            ));

        mockMvc.perform(get("/api/v1/missions/701").header(AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.participation.participationId").value("9001"))
            .andExpect(jsonPath("$.data.participation.progressCount").value(1));
    }

    @Test
    void getPublicMission_visitCount_returnsEmptyTargetContents() throws Exception {
        io.regionevent.regioneventbackend.domain.mission.entity.Mission mission = visitCountMission();
        when(getPublicMissionUseCase.get(702L, null))
            .thenReturn(new io.regionevent.regioneventbackend.domain.mission.service.PublicMissionDetailResult(mission, null));

        mockMvc.perform(get("/api/v1/missions/702"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.conditionType").value("VISIT_COUNT"))
            .andExpect(jsonPath("$.data.requiredVisitCount").value(3))
            .andExpect(jsonPath("$.data.targetContents").isEmpty());
    }

    @Test
    void getPublicMission_invalidIdAndOptionalAuthenticationFailures_returnsContractErrors() throws Exception {
        mockMvc.perform(get("/api/v1/missions/01"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(get("/api/v1/missions/not-a-number"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));
        mockMvc.perform(get("/api/v1/missions/701").header(AUTHORIZATION, "Basic malformed"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(get("/api/v1/missions/701").header(AUTHORIZATION, ""))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(get("/api/v1/missions/701").header(AUTHORIZATION, "Bearer malformed"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verifyNoInteractions(getPublicMissionUseCase);
    }

    private io.regionevent.regioneventbackend.domain.mission.entity.Mission mission() {
        io.regionevent.regioneventbackend.domain.mission.entity.Mission mission = org.mockito.Mockito.mock(
            io.regionevent.regioneventbackend.domain.mission.entity.Mission.class
        );
        io.regionevent.regioneventbackend.domain.region.entity.Region region = org.mockito.Mockito.mock(
            io.regionevent.regioneventbackend.domain.region.entity.Region.class
        );
        io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy couponPolicy = org.mockito.Mockito.mock(
            io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy.class
        );
        io.regionevent.regioneventbackend.domain.content.entity.Content content = org.mockito.Mockito.mock(
            io.regionevent.regioneventbackend.domain.content.entity.Content.class
        );
        io.regionevent.regioneventbackend.domain.mission.entity.MissionTargetContent targetContent = org.mockito.Mockito.mock(
            io.regionevent.regioneventbackend.domain.mission.entity.MissionTargetContent.class
        );
        when(mission.getMissionId()).thenReturn(701L);
        when(mission.getRegion()).thenReturn(region);
        when(region.getRegionId()).thenReturn(11L);
        when(mission.getConditionType()).thenReturn(MissionConditionType.CONTENT_SET);
        when(mission.getRequiredVisitCount()).thenReturn(null);
        when(mission.getTargetContents()).thenReturn(List.of(targetContent));
        when(targetContent.getContent()).thenReturn(content);
        when(content.getContentId()).thenReturn(101L);
        when(content.getTitle()).thenReturn("대상 콘텐츠");
        when(mission.getRewardCouponPolicy()).thenReturn(couponPolicy);
        when(couponPolicy.getCouponPolicyId()).thenReturn(501L);
        when(mission.getEndsAt()).thenReturn(Instant.parse("2026-09-30T14:59:59Z"));
        return mission;
    }

    private io.regionevent.regioneventbackend.domain.mission.entity.Mission visitCountMission() {
        io.regionevent.regioneventbackend.domain.mission.entity.Mission mission = org.mockito.Mockito.mock(
            io.regionevent.regioneventbackend.domain.mission.entity.Mission.class
        );
        io.regionevent.regioneventbackend.domain.region.entity.Region region = org.mockito.Mockito.mock(
            io.regionevent.regioneventbackend.domain.region.entity.Region.class
        );
        io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy couponPolicy = org.mockito.Mockito.mock(
            io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy.class
        );
        when(mission.getMissionId()).thenReturn(702L);
        when(mission.getRegion()).thenReturn(region);
        when(region.getRegionId()).thenReturn(11L);
        when(mission.getConditionType()).thenReturn(MissionConditionType.VISIT_COUNT);
        when(mission.getRequiredVisitCount()).thenReturn(3);
        when(mission.getTargetContents()).thenReturn(List.of());
        when(mission.getRewardCouponPolicy()).thenReturn(couponPolicy);
        when(couponPolicy.getCouponPolicyId()).thenReturn(501L);
        when(mission.getEndsAt()).thenReturn(Instant.parse("2026-09-30T14:59:59Z"));
        return mission;
    }
}
