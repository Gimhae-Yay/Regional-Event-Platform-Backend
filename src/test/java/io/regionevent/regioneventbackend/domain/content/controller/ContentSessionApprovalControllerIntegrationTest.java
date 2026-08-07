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

import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;
import io.regionevent.regioneventbackend.domain.content.service.ApproveContentSessionResult;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@ContentControllerWebMvcTest
class ContentSessionApprovalControllerIntegrationTest extends ContentControllerWebMvcTestSupport {

    private static final long SESSION_ID = 200L;
    private static final long CONTENT_ID = 100L;

    @Test
    void 회차_승인_유효한_요청이면_응답을_직렬화한다() throws Exception {
        when(approveContentSessionUseCase.approve(
            eq(AUTHENTICATED_USER_ID),
            eq(SESSION_ID),
            any(UUID.class)
        )).thenReturn(new ApproveContentSessionResult(
            SESSION_ID,
            CONTENT_ID,
            ContentSessionStatus.SCHEDULED,
            Instant.parse("2026-08-04T12:00:00Z")
        ));

        mockMvc.perform(authenticated(post("/api/v1/region-admin/sessions/{sessionId}/approve", SESSION_ID)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("회차 승인에 성공했습니다."))
            .andExpect(jsonPath("$.data.sessionId").value(String.valueOf(SESSION_ID)))
            .andExpect(jsonPath("$.data.contentId").value(String.valueOf(CONTENT_ID)))
            .andExpect(jsonPath("$.data.status").value("SCHEDULED"));

        verify(approveContentSessionUseCase).approve(
            eq(AUTHENTICATED_USER_ID),
            eq(SESSION_ID),
            any(UUID.class)
        );
    }

    @Test
    void 회차_승인_인증이_없으면_유스케이스를_호출하지_않는다() throws Exception {
        mockMvc.perform(post("/api/v1/region-admin/sessions/{sessionId}/approve", SESSION_ID))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verify(approveContentSessionUseCase, never()).approve(any(), any(), any());
    }

    @Test
    void 회차_승인_경로_ID가_양의_10진_문자열이_아니면_거부한다() throws Exception {
        for (String invalidSessionId : new String[] {"0", "-1", "01", "not-a-number"}) {
            mockMvc.perform(authenticated(post("/api/v1/region-admin/sessions/{sessionId}/approve", invalidSessionId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }
    }

    @Test
    void 회차_승인_권한과_대상_상태_오류를_공통_오류로_응답한다() throws Exception {
        expectBusinessError(ErrorCode.FORBIDDEN, 403, "FORBIDDEN");
        expectBusinessError(ErrorCode.NOT_FOUND, 404, "NOT_FOUND");
        expectBusinessError(ErrorCode.SESSION_STATE_CONFLICT, 409, "SESSION_STATE_CONFLICT");
    }

    private void expectBusinessError(ErrorCode errorCode, int statusCode, String code) throws Exception {
        when(approveContentSessionUseCase.approve(
            eq(AUTHENTICATED_USER_ID),
            eq(SESSION_ID),
            any(UUID.class)
        )).thenThrow(new BusinessException(errorCode));

        mockMvc.perform(authenticated(post("/api/v1/region-admin/sessions/{sessionId}/approve", SESSION_ID)))
            .andExpect(status().is(statusCode))
            .andExpect(jsonPath("$.code").value(code));
    }
}
