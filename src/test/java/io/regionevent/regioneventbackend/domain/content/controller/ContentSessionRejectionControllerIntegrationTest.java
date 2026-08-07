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
import io.regionevent.regioneventbackend.domain.content.service.RejectContentSessionResult;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@ContentControllerWebMvcTest
class ContentSessionRejectionControllerIntegrationTest extends ContentControllerWebMvcTestSupport {

    private static final long SESSION_ID = 201L;
    private static final long CONTENT_ID = 200L;
    private static final String REASON = "체크인 종료 시각을 조정해 주세요.";
    private static final String VALID_REQUEST = "{\"reason\":\"" + REASON + "\"}";

    @Test
    void 회차_반려_유효한_요청이면_응답을_직렬화한다() throws Exception {
        when(rejectContentSessionUseCase.reject(
            eq(AUTHENTICATED_USER_ID),
            eq(SESSION_ID),
            eq(REASON),
            any(UUID.class)
        )).thenReturn(new RejectContentSessionResult(
            SESSION_ID,
            CONTENT_ID,
            ContentSessionStatus.REJECTED,
            REASON,
            Instant.parse("2026-08-04T12:00:00Z")
        ));

        mockMvc.perform(authenticated(post("/api/v1/region-admin/sessions/{sessionId}/reject", SESSION_ID))
                .contentType(APPLICATION_JSON)
                .content(VALID_REQUEST))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("회차 반려에 성공했습니다."))
            .andExpect(jsonPath("$.data.sessionId").value(Long.toString(SESSION_ID)))
            .andExpect(jsonPath("$.data.contentId").value(Long.toString(CONTENT_ID)))
            .andExpect(jsonPath("$.data.status").value("REJECTED"))
            .andExpect(jsonPath("$.data.rejectReason").value(REASON));

        verify(rejectContentSessionUseCase).reject(
            eq(AUTHENTICATED_USER_ID),
            eq(SESSION_ID),
            eq(REASON),
            any(UUID.class)
        );
    }

    @Test
    void 회차_반려_인증이_없으면_유스케이스를_호출하지_않는다() throws Exception {
        mockMvc.perform(post("/api/v1/region-admin/sessions/{sessionId}/reject", SESSION_ID)
                .contentType(APPLICATION_JSON)
                .content(VALID_REQUEST))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verify(rejectContentSessionUseCase, never()).reject(any(), any(), any(), any());
    }

    @Test
    void 회차_반려_본문이나_경로_입력이_잘못되면_입력_오류를_반환한다() throws Exception {
        mockMvc.perform(authenticated(post("/api/v1/region-admin/sessions/{sessionId}/reject", SESSION_ID))
                .contentType(APPLICATION_JSON)
                .content("{\"reason\":\" \"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        mockMvc.perform(authenticated(post("/api/v1/region-admin/sessions/0/reject"))
                .contentType(APPLICATION_JSON)
                .content(VALID_REQUEST))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        mockMvc.perform(authenticated(post("/api/v1/region-admin/sessions/{sessionId}/reject", SESSION_ID))
                .contentType(APPLICATION_JSON)
                .content("{\"reason\":"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_JSON"));
    }

    @Test
    void 회차_반려_권한과_상태_충돌을_공통_오류로_응답한다() throws Exception {
        expectBusinessError(ErrorCode.FORBIDDEN, 403, "FORBIDDEN");
        expectBusinessError(ErrorCode.SESSION_STATE_CONFLICT, 409, "SESSION_STATE_CONFLICT");
    }

    private void expectBusinessError(ErrorCode errorCode, int statusCode, String code) throws Exception {
        when(rejectContentSessionUseCase.reject(
            eq(AUTHENTICATED_USER_ID),
            eq(SESSION_ID),
            eq(REASON),
            any(UUID.class)
        )).thenThrow(new BusinessException(errorCode));

        mockMvc.perform(authenticated(post("/api/v1/region-admin/sessions/{sessionId}/reject", SESSION_ID))
                .contentType(APPLICATION_JSON)
                .content(VALID_REQUEST))
            .andExpect(status().is(statusCode))
            .andExpect(jsonPath("$.code").value(code));
    }
}
