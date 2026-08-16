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

import org.springframework.http.MediaType;

import io.regionevent.regioneventbackend.domain.content.entity.ContentWithdrawalRequestStatus;
import io.regionevent.regioneventbackend.domain.content.service.RejectContentWithdrawalResult;

@ContentControllerWebMvcTest
class ContentWithdrawalRejectionControllerWebMvcTest extends ContentControllerWebMvcTestSupport {

    private static final long WITHDRAWAL_REQUEST_ID = 7001L;
    private static final long CONTENT_ID = 101L;

    @Test
    void 반려_결과를_200_공통_응답으로_반환한다() throws Exception {
        when(rejectContentWithdrawalUseCase.reject(
            eq(AUTHENTICATED_USER_ID),
            eq(WITHDRAWAL_REQUEST_ID),
            eq("운영 근거 부족"),
            any(UUID.class)
        )).thenReturn(new RejectContentWithdrawalResult(
            WITHDRAWAL_REQUEST_ID,
            CONTENT_ID,
            ContentWithdrawalRequestStatus.REJECTED,
            "운영 근거 부족",
            Instant.parse("2026-08-17T01:00:00Z")
        ));

        mockMvc.perform(authenticated(post(path(WITHDRAWAL_REQUEST_ID)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"reason":"  운영 근거 부족  "}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("전체 콘텐츠 철회 요청을 반려했습니다."))
            .andExpect(jsonPath("$.data.withdrawalRequestId").value("7001"))
            .andExpect(jsonPath("$.data.contentId").value("101"))
            .andExpect(jsonPath("$.data.status").value("REJECTED"))
            .andExpect(jsonPath("$.data.rejectionReason").value("운영 근거 부족"))
            .andExpect(jsonPath("$.data.rejectedAt").value("2026-08-17T01:00:00Z"));

        verify(rejectContentWithdrawalUseCase).reject(
            eq(AUTHENTICATED_USER_ID),
            eq(WITHDRAWAL_REQUEST_ID),
            eq("운영 근거 부족"),
            any(UUID.class)
        );
    }

    @Test
    void 요청_ID_형식에_따라_입력과_타입_오류를_구분한다() throws Exception {
        expectPathError("0", "INVALID_INPUT");
        expectPathError("-1", "INVALID_INPUT");
        expectPathError("01", "INVALID_INPUT");
        expectPathError("not-a-number", "INVALID_TYPE");
        expectPathError("9223372036854775808", "INVALID_TYPE");

        verify(rejectContentWithdrawalUseCase, never()).reject(any(), any(), any(), any());
    }

    @Test
    void 사유가_누락되거나_공백이면_INVALID_INPUT을_반환한다() throws Exception {
        expectBodyError("{}", "INVALID_INPUT");
        expectBodyError("{\"reason\":\"   \"}", "INVALID_INPUT");

        verify(rejectContentWithdrawalUseCase, never()).reject(any(), any(), any(), any());
    }

    @Test
    void 잘못된_JSON이면_INVALID_JSON을_반환한다() throws Exception {
        expectBodyError("{\"reason\":", "INVALID_JSON");

        verify(rejectContentWithdrawalUseCase, never()).reject(any(), any(), any(), any());
    }

    @Test
    void 인증이_없으면_유스케이스를_호출하지_않는다() throws Exception {
        mockMvc.perform(post(path(WITHDRAWAL_REQUEST_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"운영 근거 부족\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verify(rejectContentWithdrawalUseCase, never()).reject(any(), any(), any(), any());
    }

    private void expectPathError(String withdrawalRequestId, String expectedCode) throws Exception {
        mockMvc.perform(authenticated(post(path(withdrawalRequestId)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"운영 근거 부족\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(expectedCode));
    }

    private void expectBodyError(String body, String expectedCode) throws Exception {
        mockMvc.perform(authenticated(post(path(WITHDRAWAL_REQUEST_ID)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(expectedCode));
    }

    private String path(Object withdrawalRequestId) {
        return "/api/v1/region-admin/content-withdrawal-requests/"
            + withdrawalRequestId
            + "/reject";
    }
}
