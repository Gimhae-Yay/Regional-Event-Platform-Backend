package io.regionevent.regioneventbackend.domain.mission.controller;

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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.regionevent.regioneventbackend.domain.mission.entity.MissionParticipationStatus;
import io.regionevent.regioneventbackend.domain.mission.service.CreateMissionParticipationResult;
import io.regionevent.regioneventbackend.domain.mission.service.CreateMissionParticipationUseCase;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.config.SecurityConfig;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.error.GlobalExceptionHandler;
import io.regionevent.regioneventbackend.global.security.access.AccessTokenTestFactory;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshTokenStore;

@WebMvcTest(MissionParticipationController.class)
@Import({SecurityConfig.class, RequestIdFilter.class, GlobalExceptionHandler.class})
class MissionParticipationControllerWebMvcTest {

    private static final Long USER_ID = 100L;
    private static final Long MISSION_ID = 701L;
    private static final String PATH = "/api/v1/missions/{missionId}/participations";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAccessTokenService jwtAccessTokenService;

    @MockitoBean
    private CreateMissionParticipationUseCase createMissionParticipationUseCase;

    @MockitoBean
    private RefreshTokenStore refreshTokenStore;

    @Test
    void create_유효한요청이면생성응답을반환한다() throws Exception {
        when(createMissionParticipationUseCase.create(USER_ID, MISSION_ID)).thenReturn(
            new CreateMissionParticipationResult(
                9001L,
                MISSION_ID,
                MissionParticipationStatus.IN_PROGRESS,
                Instant.parse("2026-08-07T05:00:00Z")
            )
        );

        mockMvc.perform(authenticatedPost(MISSION_ID.toString()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.statusCode").value(201))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("미션 참여에 성공했습니다."))
            .andExpect(jsonPath("$.data.participationId").value("9001"))
            .andExpect(jsonPath("$.data.missionId").value("701"))
            .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
            .andExpect(jsonPath("$.data.joinedAt").value("2026-08-07T05:00:00Z"));

        verify(createMissionParticipationUseCase).create(USER_ID, MISSION_ID);
    }

    @Test
    void create_유효하지않은식별자면입력오류를반환한다() throws Exception {
        mockMvc.perform(authenticatedPost("0"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(createMissionParticipationUseCase);
    }

    @Test
    void create_변환할수없는식별자면형식오류를반환한다() throws Exception {
        mockMvc.perform(authenticatedPost("9223372036854775808"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));

        verifyNoInteractions(createMissionParticipationUseCase);
    }

    @Test
    void create_미인증이면인증오류를반환한다() throws Exception {
        mockMvc.perform(post(PATH, MISSION_ID))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verifyNoInteractions(createMissionParticipationUseCase);
    }

    @Test
    void create_비공개지역이면찾을수없음오류를반환한다() throws Exception {
        when(createMissionParticipationUseCase.create(USER_ID, MISSION_ID))
            .thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

        mockMvc.perform(authenticatedPost(MISSION_ID.toString()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void create_활성방문자가아니면권한오류를반환한다() throws Exception {
        when(createMissionParticipationUseCase.create(USER_ID, MISSION_ID))
            .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        mockMvc.perform(authenticatedPost(MISSION_ID.toString()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void create_참여불가능한미션이면상태충돌오류를반환한다() throws Exception {
        when(createMissionParticipationUseCase.create(USER_ID, MISSION_ID))
            .thenThrow(new BusinessException(ErrorCode.MISSION_STATE_CONFLICT));

        mockMvc.perform(authenticatedPost(MISSION_ID.toString()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("MISSION_STATE_CONFLICT"));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authenticatedPost(
        String missionId
    ) {
        return post(PATH, missionId)
            .header(AUTHORIZATION, "Bearer " + AccessTokenTestFactory.issueForAuthenticatedRequest(jwtAccessTokenService, USER_ID));
    }
}
