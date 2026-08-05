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
import org.springframework.http.MediaType;

import io.regionevent.regioneventbackend.domain.operator.dto.RejectOperatorApplicationResponse;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@WebMvcTest({
    OperatorApplicationApprovalController.class,
    OperatorApplicationController.class,
    OperatorApplicationDetailController.class,
    OperatorApplicationRejectionController.class,
    PendingOperatorApplicationController.class
})
class OperatorApplicationRejectionControllerWebMvcTest extends OperatorControllerWebMvcTestSupport {

    @Test
    void reject_유효한요청_반려응답을반환한다() throws Exception {
        when(rejectOperatorApplicationUseCase.reject(eq(REGION_ADMIN_ID), eq(1L), eq("사업자 정보가 부족합니다."), any()))
            .thenReturn(new RejectOperatorApplicationResponse(
                1L,
                "REJECTED",
                "사업자 정보가 부족합니다.",
                Instant.parse("2026-08-05T00:00:00Z")
            ));

        mockMvc.perform(authenticated(post("/api/v1/region-admin/operator-requests/1/reject"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rejectedReason\":\"사업자 정보가 부족합니다.\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("운영자 신청 반려에 성공했습니다."))
            .andExpect(jsonPath("$.data.operatorApplicationId").value(1))
            .andExpect(jsonPath("$.data.status").value("REJECTED"))
            .andExpect(jsonPath("$.data.rejectedReason").value("사업자 정보가 부족합니다."));

        verify(rejectOperatorApplicationUseCase).reject(
            eq(REGION_ADMIN_ID),
            eq(1L),
            eq("사업자 정보가 부족합니다."),
            any()
        );
    }

    @Test
    void reject_식별자와반려사유가유효하지않음_계약된입력오류를응답한다() throws Exception {
        mockMvc.perform(authenticated(post("/api/v1/region-admin/operator-requests/0/reject"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rejectedReason\":\"사유\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(authenticated(post("/api/v1/region-admin/operator-requests/1/reject"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rejectedReason\":\" \"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(authenticated(post("/api/v1/region-admin/operator-requests/1/reject"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rejectedReason\":"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_JSON"));

        verifyNoInteractions(rejectOperatorApplicationUseCase);
    }

    @Test
    void reject_신청상태충돌_충돌오류를응답한다() throws Exception {
        when(rejectOperatorApplicationUseCase.reject(eq(REGION_ADMIN_ID), eq(1L), any(), any()))
            .thenThrow(new BusinessException(ErrorCode.OPERATOR_APPLICATION_STATE_CONFLICT));

        mockMvc.perform(authenticated(post("/api/v1/region-admin/operator-requests/1/reject"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rejectedReason\":\"사유\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("OPERATOR_APPLICATION_STATE_CONFLICT"));
    }

    @Test
    void reject_인증정보없음_미인증오류를응답한다() throws Exception {
        mockMvc.perform(post("/api/v1/region-admin/operator-requests/1/reject")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rejectedReason\":\"사유\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

}
