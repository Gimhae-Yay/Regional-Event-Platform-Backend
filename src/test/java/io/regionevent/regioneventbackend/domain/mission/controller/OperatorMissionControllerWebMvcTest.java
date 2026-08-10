package io.regionevent.regioneventbackend.domain.mission.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import io.regionevent.regioneventbackend.domain.mission.dto.OperatorMissionDetailResponse;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionConditionType;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionStatus;
import io.regionevent.regioneventbackend.domain.mission.service.CreateOperatorMissionResult;
import io.regionevent.regioneventbackend.domain.mission.service.CreateOperatorMissionUseCase;
import io.regionevent.regioneventbackend.domain.mission.service.GetOperatorMissionDetailUseCase;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.config.SecurityConfig;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.error.GlobalExceptionHandler;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshTokenStore;

@WebMvcTest({OperatorMissionController.class, OperatorMissionDetailController.class})
@Import({SecurityConfig.class, RequestIdFilter.class, GlobalExceptionHandler.class})
class OperatorMissionControllerWebMvcTest {

    private static final long OPERATOR_ID = 100L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAccessTokenService jwtAccessTokenService;

    @MockitoBean
    private CreateOperatorMissionUseCase createOperatorMissionUseCase;

    @MockitoBean
    private GetOperatorMissionDetailUseCase getOperatorMissionDetailUseCase;

    @MockitoBean
    private RefreshTokenStore refreshTokenStore;

    @Test
    void create_withContentSetRequest_returnsCreatedDraftMission() throws Exception {
        when(createOperatorMissionUseCase.create(
            org.mockito.ArgumentMatchers.eq(OPERATOR_ID),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()
        )).thenReturn(new CreateOperatorMissionResult(701L, MissionStatus.DRAFT));

        mockMvc.perform(authenticated(post("/api/v1/operator/missions"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "conditionType": "CONTENT_SET",
                      "requiredVisitCount": null,
                      "targetContentIds": ["101", "102"],
                      "rewardCouponPolicyId": "501",
                      "endsAt": "2026-09-30T23:59:59+09:00"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.statusCode").value(201))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("미션 생성에 성공했습니다."))
            .andExpect(jsonPath("$.data.missionId").value("701"))
            .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    void create_withDuplicateTargetContentIds_returnsInputErrorWithoutCallingUseCase() throws Exception {
        mockMvc.perform(authenticated(post("/api/v1/operator/missions"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "conditionType": "CONTENT_SET",
                      "requiredVisitCount": null,
                      "targetContentIds": ["101", "101"],
                      "rewardCouponPolicyId": "501",
                      "endsAt": "2026-09-30T23:59:59+09:00"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(createOperatorMissionUseCase);
    }

    @Test
    void create_withInvalidJsonOrType_returnsContractErrorWithoutCallingUseCase() throws Exception {
        mockMvc.perform(authenticated(post("/api/v1/operator/missions"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_JSON"));

        mockMvc.perform(authenticated(post("/api/v1/operator/missions"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "conditionType": "VISIT_COUNT",
                      "requiredVisitCount": {},
                      "targetContentIds": [],
                      "rewardCouponPolicyId": "501",
                      "endsAt": "2026-09-30T23:59:59+09:00"
                    }
                    """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TYPE"));

        verifyNoInteractions(createOperatorMissionUseCase);
    }

    @Test
    void create_withScalarTypeCoercion_returnsTypeErrorWithoutCallingUseCase() throws Exception {
        mockMvc.perform(authenticated(post("/api/v1/operator/missions"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "conditionType": 1,
                      "requiredVisitCount": 3,
                      "targetContentIds": [],
                      "rewardCouponPolicyId": "501",
                      "endsAt": "2026-09-30T23:59:59+09:00"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));

        mockMvc.perform(authenticated(post("/api/v1/operator/missions"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "conditionType": "VISIT_COUNT",
                      "requiredVisitCount": "3",
                      "targetContentIds": [],
                      "rewardCouponPolicyId": "501",
                      "endsAt": "2026-09-30T23:59:59+09:00"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));

        mockMvc.perform(authenticated(post("/api/v1/operator/missions"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "conditionType": "CONTENT_SET",
                      "requiredVisitCount": null,
                      "targetContentIds": [101],
                      "rewardCouponPolicyId": "501",
                      "endsAt": "2026-09-30T23:59:59+09:00"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));

        mockMvc.perform(authenticated(post("/api/v1/operator/missions"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "conditionType": "VISIT_COUNT",
                      "requiredVisitCount": 3,
                      "targetContentIds": [],
                      "rewardCouponPolicyId": 501,
                      "endsAt": "2026-09-30T23:59:59+09:00"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));

        mockMvc.perform(authenticated(post("/api/v1/operator/missions"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "conditionType": "VISIT_COUNT",
                      "requiredVisitCount": 3,
                      "targetContentIds": [],
                      "rewardCouponPolicyId": "501",
                      "endsAt": 1788101999
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));

        verifyNoInteractions(createOperatorMissionUseCase);
    }

    @Test
    void create_withoutAuthentication_returnsUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/operator/missions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "conditionType": "VISIT_COUNT",
                      "requiredVisitCount": 3,
                      "targetContentIds": [],
                      "rewardCouponPolicyId": "501",
                      "endsAt": "2026-09-30T23:59:59+09:00"
                    }
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void create_whenUseCaseThrowsBusinessError_returnsContractError() throws Exception {
        when(createOperatorMissionUseCase.create(
            org.mockito.ArgumentMatchers.eq(OPERATOR_ID),
            org.mockito.ArgumentMatchers.argThat(command -> command.rewardCouponPolicyId().equals(501L)),
            org.mockito.ArgumentMatchers.any()
        )).thenThrow(new BusinessException(ErrorCode.MISSION_STATE_CONFLICT));
        when(createOperatorMissionUseCase.create(
            org.mockito.ArgumentMatchers.eq(OPERATOR_ID),
            org.mockito.ArgumentMatchers.argThat(command -> command.rewardCouponPolicyId().equals(502L)),
            org.mockito.ArgumentMatchers.any()
        )).thenThrow(new BusinessException(ErrorCode.FORBIDDEN));
        when(createOperatorMissionUseCase.create(
            org.mockito.ArgumentMatchers.eq(OPERATOR_ID),
            org.mockito.ArgumentMatchers.argThat(command -> command.rewardCouponPolicyId().equals(503L)),
            org.mockito.ArgumentMatchers.any()
        )).thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

        mockMvc.perform(authenticated(post("/api/v1/operator/missions"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createVisitCountRequest("501")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("MISSION_STATE_CONFLICT"));

        mockMvc.perform(authenticated(post("/api/v1/operator/missions"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createVisitCountRequest("502")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(authenticated(post("/api/v1/operator/missions"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createVisitCountRequest("503")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void getDetail_withValidMissionId_returnsOperatorMissionDetail() throws Exception {
        when(getOperatorMissionDetailUseCase.get(OPERATOR_ID, 701L)).thenReturn(new OperatorMissionDetailResponse(
            "701",
            "11",
            MissionStatus.DRAFT,
            MissionConditionType.CONTENT_SET,
            null,
            List.of(new OperatorMissionDetailResponse.TargetContentResponse("101", "Target content")),
            "501",
            OffsetDateTime.parse("2026-09-30T23:59:59+09:00"),
            null,
            null
        ));

        mockMvc.perform(authenticated(get("/api/v1/operator/missions/701")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("내 미션 상세 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.missionId").value("701"))
            .andExpect(jsonPath("$.data.regionId").value("11"))
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andExpect(jsonPath("$.data.conditionType").value("CONTENT_SET"))
            .andExpect(jsonPath("$.data.requiredVisitCount").isEmpty())
            .andExpect(jsonPath("$.data.targetContents[0].contentId").value("101"))
            .andExpect(jsonPath("$.data.targetContents[0].title").value("Target content"))
            .andExpect(jsonPath("$.data.rewardCouponPolicyId").value("501"))
            .andExpect(jsonPath("$.data.endsAt").value("2026-09-30T23:59:59+09:00"))
            .andExpect(jsonPath("$.data.publishedAt").isEmpty())
            .andExpect(jsonPath("$.data.endedAt").isEmpty());

        verify(getOperatorMissionDetailUseCase).get(OPERATOR_ID, 701L);
    }

    @Test
    void getDetail_withInvalidMissionId_returnsInputOrTypeErrorWithoutCallingUseCase() throws Exception {
        mockMvc.perform(authenticated(get("/api/v1/operator/missions/01")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(authenticated(get("/api/v1/operator/missions/not-a-number")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));

        verifyNoInteractions(getOperatorMissionDetailUseCase);
    }

    @Test
    void getDetail_withoutAuthentication_returnsUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/operator/missions/701"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void getDetail_whenUseCaseThrowsBusinessError_returnsContractError() throws Exception {
        when(getOperatorMissionDetailUseCase.get(OPERATOR_ID, 701L))
            .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));
        when(getOperatorMissionDetailUseCase.get(OPERATOR_ID, 702L))
            .thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

        mockMvc.perform(authenticated(get("/api/v1/operator/missions/701")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(authenticated(get("/api/v1/operator/missions/702")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void getDetail_withPublishedAndEndedTimestamps_serializesInstantAsUtcZ() throws Exception {
        when(getOperatorMissionDetailUseCase.get(OPERATOR_ID, 701L)).thenReturn(new OperatorMissionDetailResponse(
            "701",
            "11",
            MissionStatus.ENDED,
            MissionConditionType.VISIT_COUNT,
            3,
            List.of(),
            "501",
            OffsetDateTime.parse("2026-09-30T23:59:59+09:00"),
            Instant.parse("2026-08-01T00:00:00Z"),
            Instant.parse("2026-09-01T00:00:00Z")
        ));

        mockMvc.perform(authenticated(get("/api/v1/operator/missions/701")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.targetContents").isEmpty())
            .andExpect(jsonPath("$.data.publishedAt").value("2026-08-01T00:00:00Z"))
            .andExpect(jsonPath("$.data.endedAt").value("2026-09-01T00:00:00Z"));
    }

    private MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder requestBuilder) {
        return requestBuilder.header("Authorization", "Bearer " + jwtAccessTokenService.issue(OPERATOR_ID));
    }

    private String createVisitCountRequest(String rewardCouponPolicyId) {
        return """
            {
              "conditionType": "VISIT_COUNT",
              "requiredVisitCount": 3,
              "targetContentIds": [],
              "rewardCouponPolicyId": "%s",
              "endsAt": "2026-09-30T23:59:59+09:00"
            }
            """.formatted(rewardCouponPolicyId);
    }
}
