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
import io.regionevent.regioneventbackend.domain.mission.service.GetPublicRegionMissionsUseCase;
import io.regionevent.regioneventbackend.domain.mission.service.PublicRegionMissionListResult;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.config.SecurityConfig;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.error.GlobalExceptionHandler;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshTokenStore;

@WebMvcTest(PublicRegionMissionController.class)
@Import({SecurityConfig.class, RequestIdFilter.class, GlobalExceptionHandler.class})
class PublicRegionMissionControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAccessTokenService jwtAccessTokenService;

    @MockitoBean
    private GetPublicRegionMissionsUseCase getPublicRegionMissionsUseCase;

    @MockitoBean
    private RefreshTokenStore refreshTokenStore;

    @Test
    void getPublicRegionMissions_anonymous_returnsPageResponse() throws Exception {
        Instant endsAt = Instant.parse("2026-09-30T14:59:59Z");
        when(getPublicRegionMissionsUseCase.get(11L, null, 0, 20))
            .thenReturn(new PublicRegionMissionListResult(
                List.of(new PublicRegionMissionListResult.Mission(
                    701L,
                    11L,
                    MissionConditionType.CONTENT_SET,
                    null,
                    3,
                    endsAt,
                    null
                )),
                0,
                20,
                1,
                1
            ));

        mockMvc.perform(get("/api/v1/regions/11/missions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("공개 미션 목록 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.content[0].missionId").value("701"))
            .andExpect(jsonPath("$.data.content[0].regionId").value("11"))
            .andExpect(jsonPath("$.data.content[0].conditionType").value("CONTENT_SET"))
            .andExpect(jsonPath("$.data.content[0].targetContentCount").value(3))
            .andExpect(jsonPath("$.data.content[0].endsAt").value("2026-09-30T23:59:59+09:00"))
            .andExpect(jsonPath("$.data.content[0].participationStatus").isEmpty())
            .andExpect(jsonPath("$.data.page").value(0))
            .andExpect(jsonPath("$.data.size").value(20))
            .andExpect(jsonPath("$.data.totalElements").value(1))
            .andExpect(jsonPath("$.data.totalPages").value(1));

        verify(getPublicRegionMissionsUseCase).get(11L, null, 0, 20);
    }

    @Test
    void getPublicRegionMissions_authenticated_returnsParticipationStatus() throws Exception {
        String accessToken = jwtAccessTokenService.issue(100L);
        when(getPublicRegionMissionsUseCase.get(11L, 100L, 1, 1))
            .thenReturn(new PublicRegionMissionListResult(
                List.of(new PublicRegionMissionListResult.Mission(
                    702L,
                    11L,
                    MissionConditionType.VISIT_COUNT,
                    3,
                    0,
                    Instant.parse("2026-09-30T14:59:59Z"),
                    MissionParticipationStatus.IN_PROGRESS
                )),
                1,
                1,
                2,
                2
            ));

        mockMvc.perform(get("/api/v1/regions/11/missions")
                .header(AUTHORIZATION, "Bearer " + accessToken)
                .queryParam("page", "1")
                .queryParam("size", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content[0].participationStatus").value("IN_PROGRESS"))
            .andExpect(jsonPath("$.data.page").value(1))
            .andExpect(jsonPath("$.data.size").value(1));

        verify(getPublicRegionMissionsUseCase).get(11L, 100L, 1, 1);
    }

    @Test
    void getPublicRegionMissions_emptyResult_returnsEmptyPage() throws Exception {
        when(getPublicRegionMissionsUseCase.get(11L, null, 0, 20))
            .thenReturn(new PublicRegionMissionListResult(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/v1/regions/11/missions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content").isEmpty())
            .andExpect(jsonPath("$.data.totalElements").value(0))
            .andExpect(jsonPath("$.data.totalPages").value(0));
    }

    @Test
    void getPublicRegionMissions_invalidPathAndPagination_returnsContractError() throws Exception {
        mockMvc.perform(get("/api/v1/regions/not-a-number/missions"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));
        mockMvc.perform(get("/api/v1/regions/01/missions"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(get("/api/v1/regions/11/missions").queryParam("page", "-1"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(get("/api/v1/regions/11/missions").queryParam("size", "101"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(getPublicRegionMissionsUseCase);
    }

    @Test
    void getPublicRegionMissions_nonPublicRegion_returnsNotFound() throws Exception {
        when(getPublicRegionMissionsUseCase.get(11L, null, 0, 20))
            .thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

        mockMvc.perform(get("/api/v1/regions/11/missions"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void getPublicRegionMissions_invalidBearerToken_returnsUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/regions/11/missions")
                .header(AUTHORIZATION, "Bearer malformed"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verifyNoInteractions(getPublicRegionMissionsUseCase);
    }

    @Test
    void getPublicRegionMissions_nonBearerAuthorizationHeader_returnsUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/regions/11/missions")
                .header(AUTHORIZATION, "Basic malformed"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verifyNoInteractions(getPublicRegionMissionsUseCase);
    }
}
