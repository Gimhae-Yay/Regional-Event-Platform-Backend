package io.regionevent.regioneventbackend.domain.mission.controller;

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

import io.regionevent.regioneventbackend.domain.mission.dto.RegionAdminMissionHistoryResponse;
import io.regionevent.regioneventbackend.domain.mission.service.GetRegionAdminMissionHistoryUseCase;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.config.SecurityConfig;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.error.GlobalExceptionHandler;
import io.regionevent.regioneventbackend.global.security.access.AccessTokenTestFactory;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;

@WebMvcTest(RegionAdminMissionHistoryController.class)
@Import({SecurityConfig.class, RequestIdFilter.class, GlobalExceptionHandler.class})
class RegionAdminMissionHistoryControllerWebMvcTest {

    private static final long REGION_ADMIN_ID = 100L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAccessTokenService jwtAccessTokenService;

    @MockitoBean
    private GetRegionAdminMissionHistoryUseCase getRegionAdminMissionHistoryUseCase;

    @Test
    void getHistory_validMissionId_returnsContractResponse() throws Exception {
        when(getRegionAdminMissionHistoryUseCase.get(REGION_ADMIN_ID, 701L))
            .thenReturn(new RegionAdminMissionHistoryResponse(
                "701",
                List.of(new RegionAdminMissionHistoryResponse.HistoryResponse(
                    "12001",
                    "CREATED",
                    null,
                    "DRAFT",
                    "SUCCESS",
                    "MISSION_CREATED",
                    "USER",
                    "31",
                    Instant.parse("2026-08-07T04:20:00Z")
                ))
            ));

        mockMvc.perform(authenticated(get("/api/v1/region-admin/missions/701/history")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("미션 이력 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.missionId").value("701"))
            .andExpect(jsonPath("$.data.histories[0].auditEventId").value("12001"))
            .andExpect(jsonPath("$.data.histories[0].action").value("CREATED"))
            .andExpect(jsonPath("$.data.histories[0].previousStatus").isEmpty())
            .andExpect(jsonPath("$.data.histories[0].nextStatus").value("DRAFT"))
            .andExpect(jsonPath("$.data.histories[0].result").value("SUCCESS"))
            .andExpect(jsonPath("$.data.histories[0].reasonCode").value("MISSION_CREATED"))
            .andExpect(jsonPath("$.data.histories[0].actorKind").value("USER"))
            .andExpect(jsonPath("$.data.histories[0].actorUserId").value("31"))
            .andExpect(jsonPath("$.data.histories[0].recordedAt").value("2026-08-07T04:20:00Z"));

        verify(getRegionAdminMissionHistoryUseCase).get(REGION_ADMIN_ID, 701L);
    }

    @Test
    void getHistory_noHistory_returnsEmptyArray() throws Exception {
        when(getRegionAdminMissionHistoryUseCase.get(REGION_ADMIN_ID, 701L))
            .thenReturn(new RegionAdminMissionHistoryResponse("701", List.of()));

        mockMvc.perform(authenticated(get("/api/v1/region-admin/missions/701/history")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.histories").isArray())
            .andExpect(jsonPath("$.data.histories").isEmpty());
    }

    @Test
    void getHistory_invalidMissionId_returnsInputOrTypeErrorWithoutCallingUseCase() throws Exception {
        mockMvc.perform(authenticated(get("/api/v1/region-admin/missions/0/history")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(authenticated(get("/api/v1/region-admin/missions/not-a-number/history")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));

        verifyNoInteractions(getRegionAdminMissionHistoryUseCase);
    }

    @Test
    void getHistory_withoutAuthentication_returnsUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/region-admin/missions/701/history"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void getHistory_businessErrors_returnsContractErrors() throws Exception {
        when(getRegionAdminMissionHistoryUseCase.get(REGION_ADMIN_ID, 701L))
            .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));
        when(getRegionAdminMissionHistoryUseCase.get(REGION_ADMIN_ID, 702L))
            .thenThrow(new BusinessException(ErrorCode.NOT_FOUND));
        when(getRegionAdminMissionHistoryUseCase.get(REGION_ADMIN_ID, 703L))
            .thenThrow(new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR));

        mockMvc.perform(authenticated(get("/api/v1/region-admin/missions/701/history")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(authenticated(get("/api/v1/region-admin/missions/702/history")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mockMvc.perform(authenticated(get("/api/v1/region-admin/missions/703/history")))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));
    }

    private MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder requestBuilder) {
        return requestBuilder.header("Authorization", "Bearer " + AccessTokenTestFactory.issueForAuthenticatedRequest(jwtAccessTokenService, REGION_ADMIN_ID));
    }
}
