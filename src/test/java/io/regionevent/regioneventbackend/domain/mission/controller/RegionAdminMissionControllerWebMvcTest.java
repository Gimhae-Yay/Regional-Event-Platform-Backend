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
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import io.regionevent.regioneventbackend.domain.mission.dto.RegionAdminMissionDetailResponse;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionConditionType;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionStatus;
import io.regionevent.regioneventbackend.domain.mission.service.ApproveRegionAdminMissionResult;
import io.regionevent.regioneventbackend.domain.mission.service.ApproveRegionAdminMissionUseCase;
import io.regionevent.regioneventbackend.domain.mission.service.GetRegionAdminMissionDetailUseCase;
import io.regionevent.regioneventbackend.domain.mission.service.RejectRegionAdminMissionResult;
import io.regionevent.regioneventbackend.domain.mission.service.RejectRegionAdminMissionUseCase;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.config.SecurityConfig;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.error.GlobalExceptionHandler;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshTokenStore;

@WebMvcTest({
    RegionAdminMissionController.class,
    ApproveRegionAdminMissionController.class,
    RejectRegionAdminMissionController.class
})
@Import({SecurityConfig.class, RequestIdFilter.class, GlobalExceptionHandler.class})
class RegionAdminMissionControllerWebMvcTest {

    private static final long REGION_ADMIN_ID = 100L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAccessTokenService jwtAccessTokenService;

    @MockitoBean
    private GetRegionAdminMissionDetailUseCase getRegionAdminMissionDetailUseCase;

    @MockitoBean
    private ApproveRegionAdminMissionUseCase approveRegionAdminMissionUseCase;

    @MockitoBean
    private RejectRegionAdminMissionUseCase rejectRegionAdminMissionUseCase;

    @MockitoBean
    private RefreshTokenStore refreshTokenStore;

    @Test
    void getDetail_withValidMissionId_returnsRegionAdminMissionDetail() throws Exception {
        when(getRegionAdminMissionDetailUseCase.get(REGION_ADMIN_ID, 701L))
            .thenReturn(new RegionAdminMissionDetailResponse(
                "701",
                "11",
                MissionStatus.DRAFT,
                MissionConditionType.CONTENT_SET,
                null,
                List.of(new RegionAdminMissionDetailResponse.TargetContentResponse("101", "Target content")),
                "501",
                OffsetDateTime.parse("2026-09-30T23:59:59+09:00")
            ));

        mockMvc.perform(authenticated(get("/api/v1/region-admin/missions/701")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("지역 미션 상세 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.missionId").value("701"))
            .andExpect(jsonPath("$.data.title").doesNotExist())
            .andExpect(jsonPath("$.data.regionId").value("11"))
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andExpect(jsonPath("$.data.conditionType").value("CONTENT_SET"))
            .andExpect(jsonPath("$.data.requiredVisitCount").isEmpty())
            .andExpect(jsonPath("$.data.targetContents[0].contentId").value("101"))
            .andExpect(jsonPath("$.data.targetContents[0].title").value("Target content"))
            .andExpect(jsonPath("$.data.rewardCouponPolicyId").value("501"))
            .andExpect(jsonPath("$.data.endsAt").value("2026-09-30T23:59:59+09:00"))
            .andExpect(jsonPath("$.data.publishedAt").doesNotExist())
            .andExpect(jsonPath("$.data.endedAt").doesNotExist());

        verify(getRegionAdminMissionDetailUseCase).get(REGION_ADMIN_ID, 701L);
    }

    @Test
    void getDetail_withInvalidMissionId_returnsInputOrTypeErrorWithoutCallingUseCase() throws Exception {
        mockMvc.perform(authenticated(get("/api/v1/region-admin/missions/01")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(authenticated(get("/api/v1/region-admin/missions/not-a-number")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));

        verifyNoInteractions(getRegionAdminMissionDetailUseCase);
    }

    @Test
    void getDetail_withoutAuthentication_returnsUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/region-admin/missions/701"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void getDetail_whenUseCaseThrowsBusinessError_returnsContractError() throws Exception {
        when(getRegionAdminMissionDetailUseCase.get(REGION_ADMIN_ID, 701L))
            .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));
        when(getRegionAdminMissionDetailUseCase.get(REGION_ADMIN_ID, 702L))
            .thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

        mockMvc.perform(authenticated(get("/api/v1/region-admin/missions/701")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(authenticated(get("/api/v1/region-admin/missions/702")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void approve_withValidMissionId_returnsPublishedMission() throws Exception {
        Instant publishedAt = Instant.parse("2026-08-10T04:30:00.123456Z");
        when(approveRegionAdminMissionUseCase.approve(
            org.mockito.ArgumentMatchers.eq(REGION_ADMIN_ID),
            org.mockito.ArgumentMatchers.eq(701L),
            org.mockito.ArgumentMatchers.any(UUID.class)
        )).thenReturn(new ApproveRegionAdminMissionResult(
            701L,
            MissionStatus.PUBLISHED,
            publishedAt
        ));

        mockMvc.perform(authenticated(post("/api/v1/region-admin/missions/701/approve")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("미션 승인에 성공했습니다."))
            .andExpect(jsonPath("$.data.missionId").value("701"))
            .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
            .andExpect(jsonPath("$.data.publishedAt").value("2026-08-10T04:30:00.123456Z"));
    }

    @Test
    void approve_withInvalidMissionId_returnsInputOrTypeErrorWithoutCallingUseCase() throws Exception {
        mockMvc.perform(authenticated(post("/api/v1/region-admin/missions/01/approve")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(authenticated(post("/api/v1/region-admin/missions/not-a-number/approve")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));

        verifyNoInteractions(approveRegionAdminMissionUseCase);
    }

    @Test
    void approve_withoutAuthentication_returnsUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/region-admin/missions/701/approve"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void approve_whenUseCaseRejectsRequest_returnsDocumentedBusinessErrors() throws Exception {
        assertApprovalError(701L, ErrorCode.FORBIDDEN);
        assertApprovalError(702L, ErrorCode.NOT_FOUND);
        assertApprovalError(703L, ErrorCode.MISSION_STATE_CONFLICT);
    }

    @Test
    void reject_withValidReasonCode_returnsDraftMission() throws Exception {
        Instant rejectedAt = Instant.parse("2026-08-10T04:30:00.123456Z");
        when(rejectRegionAdminMissionUseCase.reject(
            org.mockito.ArgumentMatchers.eq(REGION_ADMIN_ID),
            org.mockito.ArgumentMatchers.eq(701L),
            org.mockito.ArgumentMatchers.eq("MISSION_REWARD_POLICY_INVALID"),
            org.mockito.ArgumentMatchers.any(UUID.class)
        )).thenReturn(new RejectRegionAdminMissionResult(701L, MissionStatus.DRAFT, rejectedAt));

        mockMvc.perform(authenticated(post("/api/v1/region-admin/missions/701/reject")
                .contentType("application/json")
                .content("{\"reasonCode\":\"MISSION_REWARD_POLICY_INVALID\"}")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.missionId").value("701"))
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andExpect(jsonPath("$.data.rejectedAt").value("2026-08-10T04:30:00.123456Z"));
    }

    @Test
    void reject_whenUseCaseRejectsInvalidReasonCode_returnsInvalidInput() throws Exception {
        when(rejectRegionAdminMissionUseCase.reject(
            org.mockito.ArgumentMatchers.eq(REGION_ADMIN_ID),
            org.mockito.ArgumentMatchers.eq(701L),
            org.mockito.ArgumentMatchers.eq("PERSONAL_OPINION"),
            org.mockito.ArgumentMatchers.any(UUID.class)
        )).thenThrow(new BusinessException(ErrorCode.INVALID_INPUT));

        mockMvc.perform(authenticated(post("/api/v1/region-admin/missions/701/reject")
                .contentType("application/json")
                .content("{\"reasonCode\":\"PERSONAL_OPINION\"}")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void reject_withInvalidMissionId_returnsInputOrTypeErrorWithoutCallingUseCase() throws Exception {
        mockMvc.perform(authenticated(post("/api/v1/region-admin/missions/01/reject")
                .contentType("application/json")
                .content("{\"reasonCode\":\"MISSION_REWARD_POLICY_INVALID\"}")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(authenticated(post("/api/v1/region-admin/missions/not-a-number/reject")
                .contentType("application/json")
                .content("{\"reasonCode\":\"MISSION_REWARD_POLICY_INVALID\"}")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));

        verifyNoInteractions(rejectRegionAdminMissionUseCase);
    }

    @Test
    void reject_withInvalidJson_returnsJsonOrTypeErrorWithoutCallingUseCase() throws Exception {
        mockMvc.perform(authenticated(post("/api/v1/region-admin/missions/701/reject")
                .contentType("application/json")
                .content("{")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_JSON"));
        mockMvc.perform(authenticated(post("/api/v1/region-admin/missions/701/reject")
                .contentType("application/json")
                .content("{\"reasonCode\":{}}")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));

        verifyNoInteractions(rejectRegionAdminMissionUseCase);
    }

    @Test
    void reject_withoutAuthentication_returnsUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/region-admin/missions/701/reject")
                .contentType("application/json")
                .content("{\"reasonCode\":\"MISSION_REWARD_POLICY_INVALID\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verifyNoInteractions(rejectRegionAdminMissionUseCase);
    }

    @Test
    void reject_whenUseCaseRejectsRequest_returnsDocumentedBusinessErrors() throws Exception {
        assertRejectionError(701L, ErrorCode.FORBIDDEN);
        assertRejectionError(702L, ErrorCode.NOT_FOUND);
        assertRejectionError(703L, ErrorCode.MISSION_STATE_CONFLICT);
    }

    private void assertApprovalError(
        long missionId,
        ErrorCode errorCode
    ) throws Exception {
        when(approveRegionAdminMissionUseCase.approve(
            org.mockito.ArgumentMatchers.eq(REGION_ADMIN_ID),
            org.mockito.ArgumentMatchers.eq(missionId),
            org.mockito.ArgumentMatchers.any(UUID.class)
        )).thenThrow(new BusinessException(errorCode));

        mockMvc.perform(authenticated(post(
                "/api/v1/region-admin/missions/{missionId}/approve",
                missionId
            )))
            .andExpect(status().is(errorCode.httpStatus().value()))
            .andExpect(jsonPath("$.code").value(errorCode.code()));
    }

    private void assertRejectionError(
        long missionId,
        ErrorCode errorCode
    ) throws Exception {
        when(rejectRegionAdminMissionUseCase.reject(
            org.mockito.ArgumentMatchers.eq(REGION_ADMIN_ID),
            org.mockito.ArgumentMatchers.eq(missionId),
            org.mockito.ArgumentMatchers.eq("MISSION_REWARD_POLICY_INVALID"),
            org.mockito.ArgumentMatchers.any(UUID.class)
        )).thenThrow(new BusinessException(errorCode));

        mockMvc.perform(authenticated(post(
                "/api/v1/region-admin/missions/{missionId}/reject",
                missionId
            )
                .contentType("application/json")
                .content("{\"reasonCode\":\"MISSION_REWARD_POLICY_INVALID\"}")))
            .andExpect(status().is(errorCode.httpStatus().value()))
            .andExpect(jsonPath("$.code").value(errorCode.code()));
    }

    private MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder requestBuilder) {
        return requestBuilder.header("Authorization", "Bearer " + jwtAccessTokenService.issue(REGION_ADMIN_ID));
    }
}
