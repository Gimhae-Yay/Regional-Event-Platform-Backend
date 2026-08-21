package io.regionevent.regioneventbackend.domain.mission.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import io.regionevent.regioneventbackend.domain.mission.entity.MissionStatus;
import io.regionevent.regioneventbackend.domain.mission.service.GetRegionAdminMissionsUseCase;
import io.regionevent.regioneventbackend.domain.mission.service.RegionAdminMissionListResult;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.config.SecurityConfig;
import io.regionevent.regioneventbackend.global.error.GlobalExceptionHandler;
import io.regionevent.regioneventbackend.global.security.access.AccessTokenTestFactory;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;

@WebMvcTest(RegionAdminMissionListController.class)
@Import({SecurityConfig.class, RequestIdFilter.class, GlobalExceptionHandler.class})
class RegionAdminMissionListControllerWebMvcTest {

    private static final long REGION_ADMIN_ID = 100L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAccessTokenService jwtAccessTokenService;

    @MockitoBean
    private GetRegionAdminMissionsUseCase getRegionAdminMissionsUseCase;

    @Test
    void getMissions_withDefaultParameters_returnsPagedMissionSummaries() throws Exception {
        when(getRegionAdminMissionsUseCase.get(REGION_ADMIN_ID, null, 0, 20))
            .thenReturn(new RegionAdminMissionListResult(
                List.of(new RegionAdminMissionListResult.MissionSummary(
                    702L,
                    "김해 문화 미션",
                    MissionStatus.PENDING_REVIEW
                )),
                0,
                20,
                1,
                1
            ));

        mockMvc.perform(authenticated(get("/api/v1/region-admin/missions")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.data.content[0].missionId").value("702"))
            .andExpect(jsonPath("$.data.content[0].title").value("김해 문화 미션"))
            .andExpect(jsonPath("$.data.content[0].status").value("PENDING_REVIEW"))
            .andExpect(jsonPath("$.data.page").value(0))
            .andExpect(jsonPath("$.data.size").value(20))
            .andExpect(jsonPath("$.data.totalElements").value(1))
            .andExpect(jsonPath("$.data.totalPages").value(1));

        verify(getRegionAdminMissionsUseCase).get(REGION_ADMIN_ID, null, 0, 20);
    }

    @Test
    void getMissions_withStatusAndInvalidParameters_returnsContractErrors() throws Exception {
        mockMvc.perform(authenticated(get("/api/v1/region-admin/missions")
            .param("status", "PENDING_REVIEW")
            .param("page", "-1")
            .param("size", "10")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(authenticated(get("/api/v1/region-admin/missions").param("size", "number")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));
        mockMvc.perform(authenticated(get("/api/v1/region-admin/missions").param("status", "INVALID")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(getRegionAdminMissionsUseCase);
    }

    @Test
    void getMissions_withSizeOutsideAllowedRange_returnsInvalidInputWithoutCallingUseCase() throws Exception {
        mockMvc.perform(authenticated(get("/api/v1/region-admin/missions").param("size", "0")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(authenticated(get("/api/v1/region-admin/missions").param("size", "101")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(getRegionAdminMissionsUseCase);
    }

    @Test
    void getMissions_withNonNumericPage_returnsInvalidTypeWithoutCallingUseCase() throws Exception {
        mockMvc.perform(authenticated(get("/api/v1/region-admin/missions").param("page", "number")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));

        verifyNoInteractions(getRegionAdminMissionsUseCase);
    }

    private MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder requestBuilder) {
        return requestBuilder.header("Authorization", "Bearer " + AccessTokenTestFactory.issueForAuthenticatedRequest(jwtAccessTokenService, REGION_ADMIN_ID));
    }
}
