package io.regionevent.regioneventbackend.domain.content.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;
import io.regionevent.regioneventbackend.domain.content.service.CancelContentSessionResult;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@ContentControllerWebMvcTest
class OperatorContentSessionControllerIntegrationTest extends ContentControllerWebMvcTestSupport {

    private static final long SESSION_ID = 300L;
    private static final String REASON = "기상 악화로 회차를 취소합니다.";
    private static final String VALID_REQUEST = "{\"cancellationReason\":\" " + REASON + " \"}";

    @Test
    void 회차_취소_유효한_요청이면_공백을_정리해_응답을_직렬화한다() throws Exception {
        when(cancelContentSessionUseCase.cancel(
            eq(AUTHENTICATED_USER_ID),
            eq(SESSION_ID),
            eq(REASON),
            any(UUID.class)
        )).thenReturn(new CancelContentSessionResult(
            SESSION_ID,
            ContentSessionStatus.CANCELLED,
            REASON,
            Instant.parse("2026-08-04T12:00:00Z")
        ));

        mockMvc.perform(authenticated(post("/api/v1/operator/sessions/{sessionId}/cancel", SESSION_ID))
                .contentType(APPLICATION_JSON)
                .content(VALID_REQUEST))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("회차 취소에 성공했습니다."))
            .andExpect(jsonPath("$.data.sessionId").value(Long.toString(SESSION_ID)))
            .andExpect(jsonPath("$.data.status").value("CANCELLED"))
            .andExpect(jsonPath("$.data.cancellationReason").value(REASON));

        verify(cancelContentSessionUseCase).cancel(
            eq(AUTHENTICATED_USER_ID),
            eq(SESSION_ID),
            eq(REASON),
            any(UUID.class)
        );
    }

    @Test
    void 회차_취소_인증이_없으면_유스케이스를_호출하지_않는다() throws Exception {
        mockMvc.perform(post("/api/v1/operator/sessions/{sessionId}/cancel", SESSION_ID)
                .contentType(APPLICATION_JSON)
                .content(VALID_REQUEST))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verify(cancelContentSessionUseCase, never()).cancel(any(), any(), any(), any());
    }

    @Test
    void 회차_취소_사유가_공백이면_입력_오류를_반환한다() throws Exception {
        mockMvc.perform(authenticated(post("/api/v1/operator/sessions/{sessionId}/cancel", SESSION_ID))
                .contentType(APPLICATION_JSON)
                .content("{\"cancellationReason\":\" \"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 회차_취소_경로_ID의_입력과_타입_오류를_구분한다() throws Exception {
        mockMvc.perform(authenticated(post("/api/v1/operator/sessions/0/cancel"))
                .contentType(APPLICATION_JSON)
                .content(VALID_REQUEST))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        mockMvc.perform(authenticated(post("/api/v1/operator/sessions/not-a-number/cancel"))
                .contentType(APPLICATION_JSON)
                .content(VALID_REQUEST))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));
    }

    @Test
    void 회차_취소_소유권이나_역할_불일치는_권한_오류로_응답한다() throws Exception {
        expectBusinessError(ErrorCode.FORBIDDEN, 403, "FORBIDDEN");
    }

    @Test
    void 회차_취소_SCHEDULED_아닌_상태면_취소_불가_오류로_응답한다() throws Exception {
        expectBusinessError(ErrorCode.SESSION_NOT_CANCELLABLE, 409, "SESSION_NOT_CANCELLABLE");
    }

    private void expectBusinessError(ErrorCode errorCode, int statusCode, String code) throws Exception {
        when(cancelContentSessionUseCase.cancel(
            eq(AUTHENTICATED_USER_ID),
            eq(SESSION_ID),
            eq(REASON),
            any(UUID.class)
        )).thenThrow(new BusinessException(errorCode));

        mockMvc.perform(authenticated(post("/api/v1/operator/sessions/{sessionId}/cancel", SESSION_ID))
                .contentType(APPLICATION_JSON)
                .content(VALID_REQUEST))
            .andExpect(status().is(statusCode))
            .andExpect(jsonPath("$.code").value(code));
    }
}
