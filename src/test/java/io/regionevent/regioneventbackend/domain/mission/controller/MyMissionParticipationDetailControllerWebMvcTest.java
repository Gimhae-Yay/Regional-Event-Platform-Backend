package io.regionevent.regioneventbackend.domain.mission.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.mission.entity.Mission;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionConditionType;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionParticipation;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionParticipationStatus;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionProgress;
import io.regionevent.regioneventbackend.domain.mission.service.GetMyMissionParticipationUseCase;
import io.regionevent.regioneventbackend.domain.mission.service.MissionParticipationDetailResult;
import io.regionevent.regioneventbackend.domain.visit.entity.Visit;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.config.SecurityConfig;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.error.GlobalExceptionHandler;
import io.regionevent.regioneventbackend.global.security.access.AccessTokenTestFactory;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshTokenStore;

@WebMvcTest(MyMissionParticipationDetailController.class)
@Import({SecurityConfig.class, RequestIdFilter.class, GlobalExceptionHandler.class})
class MyMissionParticipationDetailControllerWebMvcTest {

    private static final long VISITOR_ID = 100L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAccessTokenService jwtAccessTokenService;

    @MockitoBean
    private GetMyMissionParticipationUseCase getMyMissionParticipationUseCase;

    @MockitoBean
    private RefreshTokenStore refreshTokenStore;

    @Test
    void get_withValidParticipationId_returnsMissionParticipationDetail() throws Exception {
        Mission mission = mock(Mission.class);
        MissionParticipation participation = mock(MissionParticipation.class);
        MissionProgress progress = mock(MissionProgress.class);
        Visit visit = mock(Visit.class);
        Content content = mock(Content.class);
        when(mission.getMissionId()).thenReturn(501L);
        when(mission.getConditionType()).thenReturn(MissionConditionType.VISIT_COUNT);
        when(participation.getMissionParticipationId()).thenReturn(701L);
        when(participation.getMission()).thenReturn(mission);
        when(participation.getStatus()).thenReturn(MissionParticipationStatus.COMPLETED);
        when(participation.getJoinedAt()).thenReturn(Instant.parse("2026-08-10T00:00:00Z"));
        when(participation.getCompletedAt()).thenReturn(Instant.parse("2026-08-11T00:00:00Z"));
        when(progress.getVisit()).thenReturn(visit);
        when(progress.getContent()).thenReturn(content);
        when(progress.getRecordedAt()).thenReturn(Instant.parse("2026-08-10T01:00:00Z"));
        when(visit.getVisitId()).thenReturn(901L);
        when(content.getContentId()).thenReturn(301L);
        when(content.getTitle()).thenReturn("Target content");
        when(getMyMissionParticipationUseCase.get(VISITOR_ID, 701L)).thenReturn(
            new MissionParticipationDetailResult(participation, "김해 문화 미션", List.of(progress), 1, 3, true)
        );

        mockMvc.perform(authenticated(get("/api/v1/me/mission-participations/701")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("내 미션 참여 상세 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.participationId").value("701"))
            .andExpect(jsonPath("$.data.missionId").value("501"))
            .andExpect(jsonPath("$.data.title").value("김해 문화 미션"))
            .andExpect(jsonPath("$.data.status").value("COMPLETED"))
            .andExpect(jsonPath("$.data.conditionType").value("VISIT_COUNT"))
            .andExpect(jsonPath("$.data.progressCount").value(1))
            .andExpect(jsonPath("$.data.requiredCount").value(3))
            .andExpect(jsonPath("$.data.rewardClaimed").value(true))
            .andExpect(jsonPath("$.data.joinedAt").value("2026-08-10T00:00:00Z"))
            .andExpect(jsonPath("$.data.completedAt").value("2026-08-11T00:00:00Z"))
            .andExpect(jsonPath("$.data.progresses[0].visitId").value("901"))
            .andExpect(jsonPath("$.data.progresses[0].contentId").value("301"))
            .andExpect(jsonPath("$.data.progresses[0].contentTitle").value("Target content"))
            .andExpect(jsonPath("$.data.progresses[0].recordedAt").value("2026-08-10T01:00:00Z"));

        verify(getMyMissionParticipationUseCase).get(VISITOR_ID, 701L);
    }

    @Test
    void get_withInvalidParticipationId_returnsInputOrTypeErrorWithoutCallingUseCase() throws Exception {
        mockMvc.perform(authenticated(get("/api/v1/me/mission-participations/01")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(authenticated(get("/api/v1/me/mission-participations/not-a-number")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));

        verifyNoInteractions(getMyMissionParticipationUseCase);
    }

    @Test
    void get_withoutAuthentication_returnsUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/me/mission-participations/701"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void get_whenUseCaseThrowsBusinessError_returnsContractError() throws Exception {
        when(getMyMissionParticipationUseCase.get(VISITOR_ID, 701L))
            .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));
        when(getMyMissionParticipationUseCase.get(VISITOR_ID, 702L))
            .thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

        mockMvc.perform(authenticated(get("/api/v1/me/mission-participations/701")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(authenticated(get("/api/v1/me/mission-participations/702")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    private MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder requestBuilder) {
        return requestBuilder.header("Authorization", "Bearer " + AccessTokenTestFactory.issueForAuthenticatedRequest(jwtAccessTokenService, VISITOR_ID));
    }
}
