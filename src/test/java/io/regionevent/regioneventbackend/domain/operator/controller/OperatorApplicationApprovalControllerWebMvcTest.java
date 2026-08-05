package io.regionevent.regioneventbackend.domain.operator.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;

import io.regionevent.regioneventbackend.domain.operator.dto.ApproveOperatorApplicationResponse;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@WebMvcTest({
    OperatorApplicationApprovalController.class,
    OperatorApplicationController.class,
    OperatorApplicationDetailController.class,
    OperatorApplicationRejectionController.class,
    PendingOperatorApplicationController.class
})
class OperatorApplicationApprovalControllerWebMvcTest extends OperatorControllerWebMvcTestSupport {

    @Test
    void approve_유효한요청_승인응답을반환한다() throws Exception {
        when(approveOperatorApplicationUseCase.approve(eq(REGION_ADMIN_ID), eq(1L), any())).thenReturn(
            new ApproveOperatorApplicationResponse(1L, "APPROVED", "OPERATOR", 10L, Instant.parse("2026-08-05T00:00:00Z"))
        );

        mockMvc.perform(authenticated(post("/api/v1/region-admin/operator-requests/1/approve")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("운영자 승인에 성공했습니다."))
            .andExpect(jsonPath("$.data.operatorApplicationId").value(1))
            .andExpect(jsonPath("$.data.status").value("APPROVED"))
            .andExpect(jsonPath("$.data.operatorRole").value("OPERATOR"))
            .andExpect(jsonPath("$.data.assignedRegionId").value(10));

        verify(approveOperatorApplicationUseCase).approve(eq(REGION_ADMIN_ID), eq(1L), any());
    }

    @Test
    void approve_식별자가유효하지않음_입력또는타입오류를응답하고유스케이스를호출하지않는다() throws Exception {
        mockMvc.perform(authenticated(post("/api/v1/region-admin/operator-requests/0/approve")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(authenticated(post("/api/v1/region-admin/operator-requests/not-a-number/approve")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));

        verifyNoInteractions(approveOperatorApplicationUseCase);
    }

    @Test
    void approve_관할또는상태오류_계약된오류를응답한다() throws Exception {
        when(approveOperatorApplicationUseCase.approve(eq(REGION_ADMIN_ID), eq(1L), any()))
            .thenThrow(new BusinessException(ErrorCode.OPERATOR_APPLICATION_STATE_CONFLICT));

        mockMvc.perform(authenticated(post("/api/v1/region-admin/operator-requests/1/approve")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("OPERATOR_APPLICATION_STATE_CONFLICT"));
    }

    @Test
    void approve_인증정보없음_미인증오류를응답한다() throws Exception {
        mockMvc.perform(post("/api/v1/region-admin/operator-requests/1/approve"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

}
