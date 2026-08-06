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

import io.regionevent.regioneventbackend.domain.content.dto.CreateContentSessionRequest;
import io.regionevent.regioneventbackend.domain.content.entity.SessionRevisionStatus;
import io.regionevent.regioneventbackend.domain.content.service.CreateSessionRevisionResult;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@ContentControllerWebMvcTest
class SessionRevisionCreationControllerIntegrationTest extends ContentControllerWebMvcTestSupport {

    private static final long CONTENT_ID = 10L;
    private static final long SESSION_ID = 21L;
    private static final long REVISION_ID = 52L;
    private static final String VALID_REQUEST = """
        {
          "startsAt": "2026-08-22T10:00:00+09:00",
          "endsAt": "2026-08-22T12:00:00+09:00",
          "checkinOpenAt": "2026-08-22T09:30:00+09:00",
          "checkinCloseAt": "2026-08-22T11:30:00+09:00",
          "capacity": 30
        }
        """;

    @Test
    void 회차_수정_요청_유효하면_심사대기_수정_요청을_응답한다() throws Exception {
        when(createSessionRevisionUseCase.create(
            eq(AUTHENTICATED_USER_ID),
            eq(SESSION_ID),
            any(CreateContentSessionRequest.class),
            any(UUID.class)
        )).thenReturn(new CreateSessionRevisionResult(
            REVISION_ID,
            SessionRevisionStatus.PENDING,
            CONTENT_ID,
            SESSION_ID,
            3,
            Instant.parse("2026-08-22T01:00:00Z"),
            Instant.parse("2026-08-22T03:00:00Z"),
            Instant.parse("2026-08-22T00:30:00Z"),
            Instant.parse("2026-08-22T02:30:00Z"),
            30,
            Instant.parse("2026-08-01T01:00:00Z")
        ));

        mockMvc.perform(authenticated(post("/api/v1/operator/sessions/{sessionId}/change-requests", SESSION_ID))
                .contentType(APPLICATION_JSON)
                .content(VALID_REQUEST))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("콘텐츠 회차 수정 요청에 성공했습니다."))
            .andExpect(jsonPath("$.data.revisionId").value(Long.toString(REVISION_ID)))
            .andExpect(jsonPath("$.data.status").value("PENDING"))
            .andExpect(jsonPath("$.data.contentId").value(Long.toString(CONTENT_ID)))
            .andExpect(jsonPath("$.data.targetSessionId").value(Long.toString(SESSION_ID)))
            .andExpect(jsonPath("$.data.baseSessionVersion").value(3))
            .andExpect(jsonPath("$.data.checkinCloseAt").value("2026-08-22T11:30:00+09:00"))
            .andExpect(jsonPath("$.data.requestedAt").value("2026-08-01T01:00:00Z"));

        verify(createSessionRevisionUseCase).create(
            eq(AUTHENTICATED_USER_ID),
            eq(SESSION_ID),
            any(CreateContentSessionRequest.class),
            any(UUID.class)
        );
    }

    @Test
    void 회차_수정_요청_인증이_없으면_유스케이스를_호출하지_않는다() throws Exception {
        mockMvc.perform(post("/api/v1/operator/sessions/{sessionId}/change-requests", SESSION_ID)
                .contentType(APPLICATION_JSON)
                .content(VALID_REQUEST))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verify(createSessionRevisionUseCase, never()).create(any(), any(), any(), any());
    }

    @Test
    void 회차_수정_요청_식별자나_본문이_유효하지_않으면_입력오류를_응답한다() throws Exception {
        mockMvc.perform(authenticated(post("/api/v1/operator/sessions/0/change-requests"))
                .contentType(APPLICATION_JSON)
                .content(VALID_REQUEST))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        mockMvc.perform(authenticated(post("/api/v1/operator/sessions/not-a-number/change-requests"))
                .contentType(APPLICATION_JSON)
                .content(VALID_REQUEST))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));

        mockMvc.perform(authenticated(post("/api/v1/operator/sessions/{sessionId}/change-requests", SESSION_ID))
                .contentType(APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        mockMvc.perform(authenticated(post("/api/v1/operator/sessions/{sessionId}/change-requests", SESSION_ID))
                .contentType(APPLICATION_JSON)
                .content(VALID_REQUEST.replace("10:00:00+09:00", "10:00+09:00")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));

        mockMvc.perform(authenticated(post("/api/v1/operator/sessions/{sessionId}/change-requests", SESSION_ID))
                .contentType(APPLICATION_JSON)
                .content(VALID_REQUEST.replace("+09:00", "+09")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));
    }

    @Test
    void 회차_수정_요청_권한과_상태_오류를_공통_오류로_응답한다() throws Exception {
        expectBusinessError(ErrorCode.FORBIDDEN, 403, "FORBIDDEN");
        expectBusinessError(ErrorCode.NOT_FOUND, 404, "NOT_FOUND");
        expectBusinessError(ErrorCode.SESSION_STATE_CONFLICT, 409, "SESSION_STATE_CONFLICT");
    }

    private void expectBusinessError(ErrorCode errorCode, int statusCode, String code) throws Exception {
        when(createSessionRevisionUseCase.create(
            eq(AUTHENTICATED_USER_ID),
            eq(SESSION_ID),
            any(CreateContentSessionRequest.class),
            any(UUID.class)
        )).thenThrow(new BusinessException(errorCode));

        mockMvc.perform(authenticated(post("/api/v1/operator/sessions/{sessionId}/change-requests", SESSION_ID))
                .contentType(APPLICATION_JSON)
                .content(VALID_REQUEST))
            .andExpect(status().is(statusCode))
            .andExpect(jsonPath("$.code").value(code));
    }
}
