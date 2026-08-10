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

import io.regionevent.regioneventbackend.domain.mission.entity.MissionParticipationStatus;
import io.regionevent.regioneventbackend.domain.mission.service.GetMyMissionParticipationsUseCase;
import io.regionevent.regioneventbackend.domain.mission.service.MyMissionParticipationListResult;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.config.SecurityConfig;
import io.regionevent.regioneventbackend.global.error.GlobalExceptionHandler;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshTokenStore;

@WebMvcTest(MyMissionParticipationController.class)
@Import({SecurityConfig.class, RequestIdFilter.class, GlobalExceptionHandler.class})
class MyMissionParticipationControllerWebMvcTest {

    private static final Long USER_ID = 100L;
    private static final String PATH = "/api/v1/me/mission-participations";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAccessTokenService jwtAccessTokenService;

    @MockitoBean
    private GetMyMissionParticipationsUseCase getMyMissionParticipationsUseCase;

    @MockitoBean
    private RefreshTokenStore refreshTokenStore;

    @Test
    void getMyMissionParticipations_기본입력_명세응답을반환한다() throws Exception {
        Instant joinedAt = Instant.parse("2026-08-07T05:00:00Z");
        when(getMyMissionParticipationsUseCase.get(USER_ID, null, 0, 20)).thenReturn(
            new MyMissionParticipationListResult(
                List.of(new MyMissionParticipationListResult.Participation(
                    9001L,
                    701L,
                    MissionParticipationStatus.IN_PROGRESS,
                    1,
                    3,
                    false,
                    joinedAt,
                    null
                )),
                0,
                20,
                1,
                1
            )
        );

        mockMvc.perform(authenticatedGet())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("내 미션 참여 목록 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.content[0].participationId").value("9001"))
            .andExpect(jsonPath("$.data.content[0].missionId").value("701"))
            .andExpect(jsonPath("$.data.content[0].status").value("IN_PROGRESS"))
            .andExpect(jsonPath("$.data.content[0].progressCount").value(1))
            .andExpect(jsonPath("$.data.content[0].requiredCount").value(3))
            .andExpect(jsonPath("$.data.content[0].rewardClaimed").value(false))
            .andExpect(jsonPath("$.data.content[0].joinedAt").value("2026-08-07T05:00:00Z"))
            .andExpect(jsonPath("$.data.content[0].completedAt").doesNotExist())
            .andExpect(jsonPath("$.data.page").value(0))
            .andExpect(jsonPath("$.data.size").value(20))
            .andExpect(jsonPath("$.data.totalElements").value(1))
            .andExpect(jsonPath("$.data.totalPages").value(1));

        verify(getMyMissionParticipationsUseCase).get(USER_ID, null, 0, 20);
    }

    @Test
    void getMyMissionParticipations_상태와페이지입력_변환해전달한다() throws Exception {
        when(getMyMissionParticipationsUseCase.get(
            USER_ID,
            MissionParticipationStatus.COMPLETED,
            1,
            10
        )).thenReturn(new MyMissionParticipationListResult(List.of(), 1, 10, 1, 1));

        mockMvc.perform(authenticatedGet()
                .param("status", "COMPLETED")
                .param("page", "1")
                .param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content").isEmpty());

        verify(getMyMissionParticipationsUseCase).get(
            USER_ID,
            MissionParticipationStatus.COMPLETED,
            1,
            10
        );
    }

    @Test
    void getMyMissionParticipations_유효하지않은입력_계약오류를반환한다() throws Exception {
        mockMvc.perform(authenticatedGet().param("status", "UNKNOWN"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(authenticatedGet().param("page", "-1"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(authenticatedGet().param("size", "0"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(authenticatedGet().param("size", "101"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(authenticatedGet().param("page", "not-a-number"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));

        verifyNoInteractions(getMyMissionParticipationsUseCase);
    }

    @Test
    void getMyMissionParticipations_미인증_인증오류를반환한다() throws Exception {
        mockMvc.perform(get(PATH))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verifyNoInteractions(getMyMissionParticipationsUseCase);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authenticatedGet() {
        return get(PATH).header(AUTHORIZATION, "Bearer " + jwtAccessTokenService.issue(USER_ID));
    }
}
