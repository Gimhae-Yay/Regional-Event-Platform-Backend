package io.regionevent.regioneventbackend.domain.operator.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;

import io.regionevent.regioneventbackend.domain.operator.dto.MyOperatorApplicationResponse;

@WebMvcTest({
    MyOperatorApplicationController.class,
    OperatorApplicationApprovalController.class,
    OperatorApplicationController.class,
    OperatorApplicationDetailController.class,
    OperatorApplicationRejectionController.class,
    PendingOperatorApplicationController.class
})
class MyOperatorApplicationControllerWebMvcTest extends OperatorControllerWebMvcTestSupport {

    private static final String PATH = "/api/v1/me/operator-application";

    @Test
    void get_신청이없음_operatorApplication을명시적_null로응답한다() throws Exception {
        when(getMyOperatorApplicationUseCase.get(REGION_ADMIN_ID))
            .thenReturn(new MyOperatorApplicationResponse(null));

        mockMvc.perform(get(PATH).header(
                HttpHeaders.AUTHORIZATION,
                "Bearer " + jwtAccessTokenService.issue(REGION_ADMIN_ID, List.of())
            ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("본인 운영자 신청 현황 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.operatorApplication").value(nullValue()));

        verify(getMyOperatorApplicationUseCase).get(REGION_ADMIN_ID);
    }

    @Test
    void get_PENDING_nullable필드를명시적_null로응답한다() throws Exception {
        when(getMyOperatorApplicationUseCase.get(REGION_ADMIN_ID)).thenReturn(response(
            "PENDING",
            null,
            null
        ));

        mockMvc.perform(authenticated(get(PATH)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.operatorApplication.operatorApplicationId").value("21"))
            .andExpect(jsonPath("$.data.operatorApplication.requestedRegionId").value("1"))
            .andExpect(jsonPath("$.data.operatorApplication.createdAt").value("2026-08-20T01:15:30Z"))
            .andExpect(jsonPath("$.data.operatorApplication.reviewedAt").value(nullValue()))
            .andExpect(jsonPath("$.data.operatorApplication.rejectedReason").value(nullValue()));
    }

    @Test
    void get_APPROVED_rejectedReason을명시적_null로응답한다() throws Exception {
        when(getMyOperatorApplicationUseCase.get(REGION_ADMIN_ID)).thenReturn(response(
            "APPROVED",
            Instant.parse("2026-08-21T03:20:10Z"),
            null
        ));

        mockMvc.perform(authenticated(get(PATH)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.operatorApplication.status").value("APPROVED"))
            .andExpect(jsonPath("$.data.operatorApplication.reviewedAt").value("2026-08-21T03:20:10Z"))
            .andExpect(jsonPath("$.data.operatorApplication.rejectedReason").value(nullValue()));
    }

    @Test
    void get_REJECTED_심사시각과반려사유를응답한다() throws Exception {
        when(getMyOperatorApplicationUseCase.get(REGION_ADMIN_ID)).thenReturn(response(
            "REJECTED",
            Instant.parse("2026-08-21T03:20:10Z"),
            "사업자 정보를 확인할 수 없습니다."
        ));

        mockMvc.perform(authenticated(get(PATH)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.operatorApplication.status").value("REJECTED"))
            .andExpect(jsonPath("$.data.operatorApplication.reviewedAt").value("2026-08-21T03:20:10Z"))
            .andExpect(jsonPath("$.data.operatorApplication.rejectedReason")
                .value("사업자 정보를 확인할 수 없습니다."));
    }

    @Test
    void get_인증정보없음_미인증오류를응답한다() throws Exception {
        mockMvc.perform(get(PATH))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verifyNoInteractions(getMyOperatorApplicationUseCase);
    }

    private MyOperatorApplicationResponse response(
        String status,
        Instant reviewedAt,
        String rejectedReason
    ) {
        return new MyOperatorApplicationResponse(new MyOperatorApplicationResponse.ApplicationResponse(
            "21",
            status,
            "1",
            "김해시",
            Instant.parse("2026-08-20T01:15:30Z"),
            reviewedAt,
            rejectedReason
        ));
    }
}
