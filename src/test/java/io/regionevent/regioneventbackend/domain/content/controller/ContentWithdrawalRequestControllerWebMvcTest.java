package io.regionevent.regioneventbackend.domain.content.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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
import io.regionevent.regioneventbackend.domain.content.service.RequestContentWithdrawalResult;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@ContentControllerWebMvcTest
class ContentWithdrawalRequestControllerWebMvcTest extends ContentControllerWebMvcTestSupport {

    private static final long CONTENT_ID = 101L;
    private static final String IDEMPOTENCY_KEY = "550e8400-e29b-41d4-a716-446655440000";

    @Test
    void 철회_요청을_201_공통_응답으로_반환한다() throws Exception {
        when(requestContentWithdrawalUseCase.request(
            eq(AUTHENTICATED_USER_ID),
            eq(CONTENT_ID),
            eq(IDEMPOTENCY_KEY),
            eq("운영 계획 변경"),
            any(UUID.class)
        )).thenReturn(new RequestContentWithdrawalResult(
            7001L,
            CONTENT_ID,
            ContentWithdrawalRequestStatus.PENDING,
            "운영 계획 변경",
            Instant.parse("2026-08-16T04:00:00Z")
        ));

        mockMvc.perform(authenticated(post(
                "/api/v1/operator/contents/{contentId}/withdrawal-requests",
                CONTENT_ID
            ))
            .header("Idempotency-Key", IDEMPOTENCY_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"reason\":\"  운영 계획 변경  \"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.statusCode").value(201))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("전체 콘텐츠 철회 요청을 등록했습니다."))
            .andExpect(jsonPath("$.data.withdrawalRequestId").value("7001"))
            .andExpect(jsonPath("$.data.contentId").value("101"))
            .andExpect(jsonPath("$.data.status").value("PENDING"))
            .andExpect(jsonPath("$.data.requestReason").value("운영 계획 변경"))
            .andExpect(jsonPath("$.data.requestedAt").value("2026-08-16T04:00:00Z"));
    }

    @Test
    void 콘텐츠_ID_형식에_따라_입력과_타입_오류를_구분한다() throws Exception {
        expectPathError("0", "INVALID_INPUT");
        expectPathError("-1", "INVALID_INPUT");
        expectPathError("01", "INVALID_INPUT");
        expectPathError("not-a-number", "INVALID_TYPE");
        expectPathError("9223372036854775808", "INVALID_TYPE");

        verify(requestContentWithdrawalUseCase, never()).request(
            eq(AUTHENTICATED_USER_ID),
            any(),
            any(),
            any(),
            any()
        );
    }

    @Test
    void 멱등_키가_없으면_입력_오류를_반환한다() throws Exception {
        doThrow(new BusinessException(ErrorCode.INVALID_INPUT))
            .when(requestContentWithdrawalUseCase)
            .request(
                eq(AUTHENTICATED_USER_ID),
                eq(CONTENT_ID),
                eq(null),
                eq("운영 계획 변경"),
                any(UUID.class)
            );

        mockMvc.perform(authenticated(post(
                "/api/v1/operator/contents/{contentId}/withdrawal-requests",
                CONTENT_ID
            ))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"reason\":\"운영 계획 변경\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 사유가_없거나_공백이면_입력_오류를_반환한다() throws Exception {
        mockMvc.perform(authenticated(post(
                "/api/v1/operator/contents/{contentId}/withdrawal-requests",
                CONTENT_ID
            ))
            .header("Idempotency-Key", IDEMPOTENCY_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"reason\":\"   \"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 인증이_없으면_유스케이스를_호출하지_않는다() throws Exception {
        mockMvc.perform(post(
                "/api/v1/operator/contents/{contentId}/withdrawal-requests",
                CONTENT_ID
            )
            .header("Idempotency-Key", IDEMPOTENCY_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"reason\":\"운영 계획 변경\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verify(requestContentWithdrawalUseCase, never()).request(any(), any(), any(), any(), any());
    }

    private void expectPathError(String contentId, String expectedCode) throws Exception {
        mockMvc.perform(authenticated(post(
                "/api/v1/operator/contents/{contentId}/withdrawal-requests",
                contentId
            ))
            .header("Idempotency-Key", IDEMPOTENCY_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"reason\":\"운영 계획 변경\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(expectedCode));
    }
}
