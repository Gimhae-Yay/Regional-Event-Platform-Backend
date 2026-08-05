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
import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;
import io.regionevent.regioneventbackend.domain.content.service.CreateContentSessionResult;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@ContentControllerWebMvcTest
class CreateContentSessionControllerIntegrationTest extends ContentControllerWebMvcTestSupport {

    private static final long CONTENT_ID = 200L;
    private static final long SESSION_ID = 201L;
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
    void 회차_생성_유효한_요청이면_심사대기_회차를_응답한다() throws Exception {
        when(createContentSessionUseCase.create(
            eq(AUTHENTICATED_USER_ID),
            eq(CONTENT_ID),
            any(CreateContentSessionRequest.class),
            any(UUID.class)
        )).thenReturn(new CreateContentSessionResult(
            SESSION_ID,
            CONTENT_ID,
            ContentSessionStatus.PENDING,
            Instant.parse("2026-08-22T01:00:00Z"),
            Instant.parse("2026-08-22T03:00:00Z"),
            Instant.parse("2026-08-22T00:30:00Z"),
            Instant.parse("2026-08-22T02:30:00Z"),
            30,
            30,
            Instant.parse("2026-08-01T01:00:00Z")
        ));

        mockMvc.perform(authenticated(post("/api/v1/operator/contents/{contentId}/sessions", CONTENT_ID))
                .contentType(APPLICATION_JSON)
                .content(VALID_REQUEST))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("콘텐츠 회차 생성에 성공했습니다."))
            .andExpect(jsonPath("$.data.sessionId").value(Long.toString(SESSION_ID)))
            .andExpect(jsonPath("$.data.status").value("PENDING"))
            .andExpect(jsonPath("$.data.remainingCapacity").value(30));

        verify(createContentSessionUseCase).create(
            eq(AUTHENTICATED_USER_ID),
            eq(CONTENT_ID),
            any(CreateContentSessionRequest.class),
            any(UUID.class)
        );
    }

    @Test
    void 회차_생성_인증이_없으면_유스케이스를_호출하지_않는다() throws Exception {
        mockMvc.perform(post("/api/v1/operator/contents/{contentId}/sessions", CONTENT_ID)
                .contentType(APPLICATION_JSON)
                .content(VALID_REQUEST))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verify(createContentSessionUseCase, never()).create(any(), any(), any(), any());
    }

    @Test
    void 회차_생성_경로_식별자나_본문이_유효하지_않으면_입력오류를_응답한다() throws Exception {
        mockMvc.perform(authenticated(post("/api/v1/operator/contents/0/sessions"))
                .contentType(APPLICATION_JSON)
                .content(VALID_REQUEST))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        mockMvc.perform(authenticated(post("/api/v1/operator/contents/{contentId}/sessions", CONTENT_ID))
                .contentType(APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 회차_생성_권한과_대상_오류를_공통_오류로_응답한다() throws Exception {
        expectBusinessError(ErrorCode.FORBIDDEN, 403, "FORBIDDEN");
        expectBusinessError(ErrorCode.NOT_FOUND, 404, "NOT_FOUND");
    }

    private void expectBusinessError(ErrorCode errorCode, int statusCode, String code) throws Exception {
        when(createContentSessionUseCase.create(
            eq(AUTHENTICATED_USER_ID),
            eq(CONTENT_ID),
            any(CreateContentSessionRequest.class),
            any(UUID.class)
        )).thenThrow(new BusinessException(errorCode));

        mockMvc.perform(authenticated(post("/api/v1/operator/contents/{contentId}/sessions", CONTENT_ID))
                .contentType(APPLICATION_JSON)
                .content(VALID_REQUEST))
            .andExpect(status().is(statusCode))
            .andExpect(jsonPath("$.code").value(code));
    }
}
