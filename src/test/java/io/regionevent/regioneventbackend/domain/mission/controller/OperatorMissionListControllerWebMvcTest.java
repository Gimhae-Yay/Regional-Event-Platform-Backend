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

import io.regionevent.regioneventbackend.domain.mission.entity.MissionConditionType;
import io.regionevent.regioneventbackend.domain.mission.entity.MissionStatus;
import io.regionevent.regioneventbackend.domain.mission.service.GetOperatorMissionsUseCase;
import io.regionevent.regioneventbackend.domain.mission.service.OperatorMissionListResult;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.config.SecurityConfig;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.error.GlobalExceptionHandler;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;
import io.regionevent.regioneventbackend.global.security.refresh.RefreshTokenStore;

@WebMvcTest(OperatorMissionListController.class)
@Import({SecurityConfig.class, RequestIdFilter.class, GlobalExceptionHandler.class})
class OperatorMissionListControllerWebMvcTest {

    private static final long OPERATOR_ID = 100L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAccessTokenService jwtAccessTokenService;

    @MockitoBean
    private GetOperatorMissionsUseCase getOperatorMissionsUseCase;

    @MockitoBean
    private RefreshTokenStore refreshTokenStore;

    @Test
    void getMissions_withDefaultParameters_returnsPagedMissionSummaries() throws Exception {
        when(getOperatorMissionsUseCase.get(OPERATOR_ID, null, 0, 20))
            .thenReturn(new OperatorMissionListResult(
                List.of(new OperatorMissionListResult.MissionSummary(
                    702L,
                    MissionStatus.PUBLISHED,
                    MissionConditionType.CONTENT_SET,
                    Instant.parse("2026-09-30T14:59:59Z")
                )),
                0,
                20,
                1,
                1
            ));

        mockMvc.perform(authenticated(get("/api/v1/operator/missions")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("내 미션 목록 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.content[0].missionId").value("702"))
            .andExpect(jsonPath("$.data.content[0].status").value("PUBLISHED"))
            .andExpect(jsonPath("$.data.content[0].conditionType").value("CONTENT_SET"))
            .andExpect(jsonPath("$.data.content[0].endsAt").value("2026-09-30T23:59:59+09:00"))
            .andExpect(jsonPath("$.data.page").value(0))
            .andExpect(jsonPath("$.data.size").value(20))
            .andExpect(jsonPath("$.data.totalElements").value(1))
            .andExpect(jsonPath("$.data.totalPages").value(1));

        verify(getOperatorMissionsUseCase).get(OPERATOR_ID, null, 0, 20);
    }

    @Test
    void getMissions_withStatusAndPageParameters_passesRequestedValues() throws Exception {
        when(getOperatorMissionsUseCase.get(OPERATOR_ID, MissionStatus.ENDED, 2, 5))
            .thenReturn(new OperatorMissionListResult(List.of(), 2, 5, 0, 0));

        mockMvc.perform(authenticated(get("/api/v1/operator/missions")
                .param("status", "ENDED")
                .param("page", "2")
                .param("size", "5")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content").isEmpty())
            .andExpect(jsonPath("$.data.page").value(2))
            .andExpect(jsonPath("$.data.size").value(5));

        verify(getOperatorMissionsUseCase).get(OPERATOR_ID, MissionStatus.ENDED, 2, 5);
    }

    @Test
    void getMissions_withInvalidStatusOrRange_returnsInvalidInput() throws Exception {
        mockMvc.perform(authenticated(get("/api/v1/operator/missions").param("status", "INVALID")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(authenticated(get("/api/v1/operator/missions").param("page", "-1")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(authenticated(get("/api/v1/operator/missions").param("size", "0")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(authenticated(get("/api/v1/operator/missions").param("size", "101")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(getOperatorMissionsUseCase);
    }

    @Test
    void getMissions_withNonNumericPageOrSize_returnsInvalidType() throws Exception {
        mockMvc.perform(authenticated(get("/api/v1/operator/missions").param("page", "number")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));
        mockMvc.perform(authenticated(get("/api/v1/operator/missions").param("size", "number")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));

        verifyNoInteractions(getOperatorMissionsUseCase);
    }

    @Test
    void getMissions_withoutAuthentication_returnsUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/operator/missions"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verifyNoInteractions(getOperatorMissionsUseCase);
    }

    @Test
    void getMissions_whenUseCaseThrowsForbidden_returnsForbidden() throws Exception {
        when(getOperatorMissionsUseCase.get(OPERATOR_ID, null, 0, 20))
            .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        mockMvc.perform(authenticated(get("/api/v1/operator/missions")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    private MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder requestBuilder) {
        return requestBuilder.header("Authorization", "Bearer " + jwtAccessTokenService.issue(OPERATOR_ID));
    }
}
