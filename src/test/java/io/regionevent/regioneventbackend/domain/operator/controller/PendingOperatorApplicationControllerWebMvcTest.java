package io.regionevent.regioneventbackend.domain.operator.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;

import io.regionevent.regioneventbackend.domain.operator.dto.PendingOperatorApplicationsResponse;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@WebMvcTest({
    OperatorApplicationApprovalController.class,
    OperatorApplicationController.class,
    OperatorApplicationDetailController.class,
    OperatorApplicationRejectionController.class,
    PendingOperatorApplicationController.class
})
class PendingOperatorApplicationControllerWebMvcTest extends OperatorControllerWebMvcTestSupport {

    private static final String PATH = "/api/v1/region-admin/operator-requests";

    @Test
    void getPendingApplications_대기신청존재_사업자정보없이목록을응답한다() throws Exception {
        when(getPendingOperatorApplicationsUseCase.get(REGION_ADMIN_ID, "PENDING")).thenReturn(
            new PendingOperatorApplicationsResponse(List.of(
                new PendingOperatorApplicationsResponse.OperatorRequest(
                    1L,
                    2L,
                    10L,
                    OffsetDateTime.parse("2026-08-05T09:00:00+09:00")
                )
            ))
        );

        mockMvc.perform(authenticated(get(PATH).param("status", "PENDING")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("운영자 승인 요청 대기 목록 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.operatorRequests.length()").value(1))
            .andExpect(jsonPath("$.data.operatorRequests[0].operatorApplicationId").value(1))
            .andExpect(jsonPath("$.data.operatorRequests[0].applicantUserId").value(2))
            .andExpect(jsonPath("$.data.operatorRequests[0].requestedRegionId").value(10))
            .andExpect(jsonPath("$.data.operatorRequests[0].businessInformation").doesNotExist());

        verify(getPendingOperatorApplicationsUseCase).get(REGION_ADMIN_ID, "PENDING");
    }

    @Test
    void getPendingApplications_대기신청없음_빈배열을응답한다() throws Exception {
        when(getPendingOperatorApplicationsUseCase.get(REGION_ADMIN_ID, "PENDING"))
            .thenReturn(new PendingOperatorApplicationsResponse(List.of()));

        mockMvc.perform(authenticated(get(PATH).param("status", "PENDING")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.operatorRequests").isArray())
            .andExpect(jsonPath("$.data.operatorRequests").isEmpty());
    }

    @Test
    void getPendingApplications_잘못된상태또는권한_계약된오류를응답한다() throws Exception {
        when(getPendingOperatorApplicationsUseCase.get(REGION_ADMIN_ID, "APPROVED"))
            .thenThrow(new BusinessException(ErrorCode.INVALID_INPUT));
        when(getPendingOperatorApplicationsUseCase.get(REGION_ADMIN_ID, "PENDING"))
            .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        mockMvc.perform(authenticated(get(PATH).param("status", "APPROVED")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(authenticated(get(PATH).param("status", "PENDING")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(authenticated(get(PATH)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void getPendingApplications_인증정보없음_미인증오류를응답한다() throws Exception {
        mockMvc.perform(get(PATH).param("status", "PENDING"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verifyNoInteractions(getPendingOperatorApplicationsUseCase);
    }

}
