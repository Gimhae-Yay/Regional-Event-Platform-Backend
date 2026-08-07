package io.regionevent.regioneventbackend.domain.operator.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;

import io.regionevent.regioneventbackend.domain.operator.dto.OperatorApplicationDetailResponse;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@WebMvcTest({
    OperatorApplicationApprovalController.class,
    OperatorApplicationController.class,
    OperatorApplicationDetailController.class,
    OperatorApplicationRejectionController.class,
    PendingOperatorApplicationController.class
})
class OperatorApplicationDetailControllerWebMvcTest extends OperatorControllerWebMvcTestSupport {

    @Test
    void getDetail_유효한요청_상세응답과캐시금지를반환한다() throws Exception {
        when(getOperatorApplicationDetailUseCase.get(REGION_ADMIN_ID, 1L)).thenReturn(
            new OperatorApplicationDetailResponse(
                1L,
                2L,
                10L,
                "사업자등록번호 123-45-67890",
                "PENDING",
                null,
                null,
                OffsetDateTime.parse("2026-08-05T09:00:00+09:00"),
                OffsetDateTime.parse("2026-08-05T09:00:00+09:00")
            )
        );

        mockMvc.perform(authenticated(get("/api/v1/region-admin/operator-requests/1")))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("운영자 승인 요청 상세 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.operatorApplicationId").value(1))
            .andExpect(jsonPath("$.data.requestedRegionId").value(10))
            .andExpect(jsonPath("$.data.businessInformation").value("사업자등록번호 123-45-67890"));

        verify(getOperatorApplicationDetailUseCase).get(REGION_ADMIN_ID, 1L);
    }

    @Test
    void getDetail_식별자가유효하지않음_입력또는타입오류를응답하고유스케이스를호출하지않는다() throws Exception {
        mockMvc.perform(authenticated(get("/api/v1/region-admin/operator-requests/01")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(authenticated(get("/api/v1/region-admin/operator-requests/not-a-number")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));

        verifyNoInteractions(getOperatorApplicationDetailUseCase);
    }

    @Test
    void getDetail_관할밖신청_찾을수없음오류를응답한다() throws Exception {
        when(getOperatorApplicationDetailUseCase.get(REGION_ADMIN_ID, 1L))
            .thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

        mockMvc.perform(authenticated(get("/api/v1/region-admin/operator-requests/1")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void getDetail_인증정보없음_미인증오류를응답한다() throws Exception {
        mockMvc.perform(get("/api/v1/region-admin/operator-requests/1"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

}
