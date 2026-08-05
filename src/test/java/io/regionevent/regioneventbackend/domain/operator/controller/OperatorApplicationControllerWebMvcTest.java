package io.regionevent.regioneventbackend.domain.operator.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;

import io.regionevent.regioneventbackend.domain.operator.dto.CreateOperatorApplicationResponse;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@WebMvcTest({
    OperatorApplicationApprovalController.class,
    OperatorApplicationController.class,
    OperatorApplicationDetailController.class,
    OperatorApplicationRejectionController.class,
    PendingOperatorApplicationController.class
})
class OperatorApplicationControllerWebMvcTest extends OperatorControllerWebMvcTestSupport {

    @Test
    void create_유효한요청_운영자신청응답을반환한다() throws Exception {
        when(reapplyOperatorApplicationUseCase.reapply(eq(REGION_ADMIN_ID), any()))
            .thenReturn(new CreateOperatorApplicationResponse(1L, 10L, "PENDING"));

        mockMvc.perform(authenticated(post("/api/v1/operator/operator-requests"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.statusCode").value(201))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("운영자 권한 신청에 성공했습니다."))
            .andExpect(jsonPath("$.data.operatorApplicationId").value(1))
            .andExpect(jsonPath("$.data.requestedRegionId").value(10))
            .andExpect(jsonPath("$.data.status").value("PENDING"));

        verify(reapplyOperatorApplicationUseCase).reapply(eq(REGION_ADMIN_ID), any());
    }

    @Test
    void create_요청검증실패_입력오류를응답하고유스케이스를호출하지않는다() throws Exception {
        mockMvc.perform(authenticated(post("/api/v1/operator/operator-requests"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{" + "\"requestedRegionId\":0,\"businessInformation\":\" \"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(reapplyOperatorApplicationUseCase);
    }

    @Test
    void create_재신청불가_상태충돌을응답한다() throws Exception {
        when(reapplyOperatorApplicationUseCase.reapply(eq(REGION_ADMIN_ID), any()))
            .thenThrow(new BusinessException(ErrorCode.OPERATOR_APPLICATION_PENDING));

        mockMvc.perform(authenticated(post("/api/v1/operator/operator-requests"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("OPERATOR_APPLICATION_PENDING"));
    }

    @Test
    void create_인증정보없음_미인증오류를응답한다() throws Exception {
        mockMvc.perform(post("/api/v1/operator/operator-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    private String validRequest() {
        return "{" + "\"requestedRegionId\":10,\"businessInformation\":\"사업자등록번호 123-45-67890\"}";
    }
}
