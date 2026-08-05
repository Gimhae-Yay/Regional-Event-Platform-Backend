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

import io.regionevent.regioneventbackend.domain.content.entity.SessionRevisionStatus;
import io.regionevent.regioneventbackend.domain.content.service.RejectSessionRevisionResult;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@ContentControllerWebMvcTest
class SessionRevisionRejectionControllerWebMvcTest extends ContentControllerWebMvcTestSupport {

    private static final long REVISION_ID = 52L;
    private static final String REASON = "정원 변경 사유를 보완해 주세요.";

    @Test
    void 유효한_반려_요청이면_명세_형식으로_응답한다() throws Exception {
        when(rejectSessionRevisionUseCase.reject(
            eq(AUTHENTICATED_USER_ID),
            eq(REVISION_ID),
            eq(REASON),
            any(UUID.class)
        )).thenReturn(new RejectSessionRevisionResult(
            REVISION_ID,
            SessionRevisionStatus.REJECTED,
            10L,
            21L,
            REASON,
            Instant.parse("2026-08-05T01:00:00Z")
        ));

        mockMvc.perform(authenticated(post("/api/v1/region-admin/session-revisions/{revisionId}/reject", REVISION_ID)
                .contentType(APPLICATION_JSON)
                .content("{\"reason\":\"" + REASON + "\"}")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("회차 수정 요청 반려에 성공했습니다."))
            .andExpect(jsonPath("$.data.revisionId").value("52"))
            .andExpect(jsonPath("$.data.revisionStatus").value("REJECTED"))
            .andExpect(jsonPath("$.data.contentId").value("10"))
            .andExpect(jsonPath("$.data.targetSessionId").value("21"))
            .andExpect(jsonPath("$.data.rejectReason").value(REASON));

        verify(rejectSessionRevisionUseCase).reject(
            eq(AUTHENTICATED_USER_ID),
            eq(REVISION_ID),
            eq(REASON),
            any(UUID.class)
        );
    }

    @Test
    void 미인증_요청이면_UseCase를_호출하지_않는다() throws Exception {
        mockMvc.perform(post("/api/v1/region-admin/session-revisions/{revisionId}/reject", REVISION_ID)
                .contentType(APPLICATION_JSON)
                .content("{\"reason\":\"" + REASON + "\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verify(rejectSessionRevisionUseCase, never()).reject(any(), any(), any(), any());
    }

    @Test
    void 잘못된_JSON과_입력값은_입력_오류로_응답한다() throws Exception {
        mockMvc.perform(authenticated(post("/api/v1/region-admin/session-revisions/{revisionId}/reject", REVISION_ID)
                .contentType(APPLICATION_JSON)
                .content("{")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_JSON"));
        mockMvc.perform(authenticated(post("/api/v1/region-admin/session-revisions/0/reject"))
                .contentType(APPLICATION_JSON)
                .content("{\"reason\":\" \"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 권한_거부와_상태_충돌은_공통_오류_계약으로_응답한다() throws Exception {
        expectBusinessError(ErrorCode.FORBIDDEN, 403, "FORBIDDEN");
        expectBusinessError(ErrorCode.NOT_FOUND, 404, "NOT_FOUND");
        expectBusinessError(ErrorCode.SESSION_STATE_CONFLICT, 409, "SESSION_STATE_CONFLICT");
    }

    private void expectBusinessError(ErrorCode errorCode, int statusCode, String code) throws Exception {
        when(rejectSessionRevisionUseCase.reject(
            eq(AUTHENTICATED_USER_ID),
            eq(REVISION_ID),
            eq(REASON),
            any(UUID.class)
        )).thenThrow(new BusinessException(errorCode));

        mockMvc.perform(authenticated(post("/api/v1/region-admin/session-revisions/{revisionId}/reject", REVISION_ID)
                .contentType(APPLICATION_JSON)
                .content("{\"reason\":\"" + REASON + "\"}")))
            .andExpect(status().is(statusCode))
            .andExpect(jsonPath("$.code").value(code));
    }
}
