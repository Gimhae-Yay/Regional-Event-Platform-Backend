package io.regionevent.regioneventbackend.domain.content.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentWithdrawalRequestStatus;
import io.regionevent.regioneventbackend.domain.content.service.ApproveContentWithdrawalResult;

@ContentControllerWebMvcTest
class ContentWithdrawalApprovalControllerWebMvcTest extends ContentControllerWebMvcTestSupport {

    private static final long WITHDRAWAL_REQUEST_ID = 7001L;
    private static final long CONTENT_ID = 101L;

    @Test
    void 승인_결과를_200_공통_응답으로_반환한다() throws Exception {
        when(approveContentWithdrawalUseCase.approve(
            eq(AUTHENTICATED_USER_ID),
            eq(WITHDRAWAL_REQUEST_ID),
            any(UUID.class)
        )).thenReturn(new ApproveContentWithdrawalResult(
            WITHDRAWAL_REQUEST_ID,
            ContentWithdrawalRequestStatus.APPROVED,
            CONTENT_ID,
            ContentStatus.WITHDRAWN,
            "운영 계획 변경",
            Instant.parse("2026-08-16T06:00:00Z")
        ));

        mockMvc.perform(authenticated(post(
                "/api/v1/region-admin/content-withdrawal-requests/{withdrawalRequestId}/approve",
                WITHDRAWAL_REQUEST_ID
            )))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("전체 콘텐츠 철회 요청을 승인했습니다."))
            .andExpect(jsonPath("$.data.withdrawalRequestId").value("7001"))
            .andExpect(jsonPath("$.data.requestStatus").value("APPROVED"))
            .andExpect(jsonPath("$.data.contentId").value("101"))
            .andExpect(jsonPath("$.data.contentStatus").value("WITHDRAWN"))
            .andExpect(jsonPath("$.data.withdrawalReason").value("운영 계획 변경"))
            .andExpect(jsonPath("$.data.approvedAt").value("2026-08-16T06:00:00Z"));
    }

    @Test
    void 요청_ID_형식에_따라_입력과_타입_오류를_구분한다() throws Exception {
        expectPathError("0", "INVALID_INPUT");
        expectPathError("-1", "INVALID_INPUT");
        expectPathError("01", "INVALID_INPUT");
        expectPathError("not-a-number", "INVALID_TYPE");
        expectPathError("9223372036854775808", "INVALID_TYPE");

        verify(approveContentWithdrawalUseCase, never()).approve(any(), any(), any());
    }

    @Test
    void 인증이_없으면_유스케이스를_호출하지_않는다() throws Exception {
        mockMvc.perform(post(
                "/api/v1/region-admin/content-withdrawal-requests/{withdrawalRequestId}/approve",
                WITHDRAWAL_REQUEST_ID
            ))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verify(approveContentWithdrawalUseCase, never()).approve(any(), any(), any());
    }

    private void expectPathError(String withdrawalRequestId, String expectedCode) throws Exception {
        mockMvc.perform(authenticated(post(
                "/api/v1/region-admin/content-withdrawal-requests/{withdrawalRequestId}/approve",
                withdrawalRequestId
            )))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(expectedCode));
    }
}
