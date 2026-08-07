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

import io.regionevent.regioneventbackend.domain.content.entity.ContentRevisionStatus;
import io.regionevent.regioneventbackend.domain.content.service.WithdrawContentRevisionResult;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@ContentControllerWebMvcTest
class WithdrawContentRevisionControllerIntegrationTest extends ContentControllerWebMvcTestSupport {

    private static final long REVISION_ID = 201L;
    private static final long CONTENT_ID = 200L;
    private static final String REASON = "운영자 요청으로 철회합니다.";
    private static final String VALID_REQUEST = "{\"reason\":\"" + REASON + "\"}";

    @Test
    void 수정본_철회_유효한_요청이면_응답을_직렬화한다() throws Exception {
        when(withdrawContentRevisionUseCase.withdraw(
            eq(AUTHENTICATED_USER_ID),
            eq(REVISION_ID),
            eq(REASON),
            any(UUID.class)
        )).thenReturn(new WithdrawContentRevisionResult(
            REVISION_ID,
            CONTENT_ID,
            ContentRevisionStatus.EDIT_WITHDRAWN,
            REASON,
            Instant.parse("2026-08-04T12:00:00Z")
        ));

        mockMvc.perform(authenticated(post("/api/v1/operator/content-revisions/{revisionId}/withdraw", REVISION_ID))
                .contentType(APPLICATION_JSON)
                .content(VALID_REQUEST))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("콘텐츠 수정본 철회에 성공했습니다."))
            .andExpect(jsonPath("$.data.revisionId").value(Long.toString(REVISION_ID)))
            .andExpect(jsonPath("$.data.contentId").value(Long.toString(CONTENT_ID)))
            .andExpect(jsonPath("$.data.status").value("EDIT_WITHDRAWN"))
            .andExpect(jsonPath("$.data.withdrawalReason").value(REASON));

        verify(withdrawContentRevisionUseCase).withdraw(
            eq(AUTHENTICATED_USER_ID),
            eq(REVISION_ID),
            eq(REASON),
            any(UUID.class)
        );
    }

    @Test
    void 수정본_철회_인증이_없으면_유스케이스를_호출하지_않는다() throws Exception {
        mockMvc.perform(post("/api/v1/operator/content-revisions/{revisionId}/withdraw", REVISION_ID)
                .contentType(APPLICATION_JSON)
                .content(VALID_REQUEST))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verify(withdrawContentRevisionUseCase, never()).withdraw(any(), any(), any(), any());
    }

    @Test
    void 수정본_철회_사유가_공백이면_입력_오류를_반환한다() throws Exception {
        mockMvc.perform(authenticated(post("/api/v1/operator/content-revisions/{revisionId}/withdraw", REVISION_ID))
                .contentType(APPLICATION_JSON)
                .content("{\"reason\":\" \"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 수정본_철회_경로_ID가_양의_정수가_아니면_입력_오류를_반환한다() throws Exception {
        mockMvc.perform(authenticated(post("/api/v1/operator/content-revisions/0/withdraw"))
                .contentType(APPLICATION_JSON)
                .content(VALID_REQUEST))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 수정본_철회_권한_거절은_공통_오류로_응답한다() throws Exception {
        expectBusinessError(ErrorCode.FORBIDDEN, 403, "FORBIDDEN");
    }

    @Test
    void 수정본_철회_대상이_없으면_공통_오류로_응답한다() throws Exception {
        expectBusinessError(ErrorCode.NOT_FOUND, 404, "NOT_FOUND");
    }

    @Test
    void 수정본_철회_상태_충돌은_공통_오류로_응답한다() throws Exception {
        expectBusinessError(ErrorCode.CONTENT_STATE_CONFLICT, 409, "CONTENT_STATE_CONFLICT");
    }

    private void expectBusinessError(ErrorCode errorCode, int statusCode, String code) throws Exception {
        when(withdrawContentRevisionUseCase.withdraw(
            eq(AUTHENTICATED_USER_ID),
            eq(REVISION_ID),
            eq(REASON),
            any(UUID.class)
        )).thenThrow(new BusinessException(errorCode));

        mockMvc.perform(authenticated(post("/api/v1/operator/content-revisions/{revisionId}/withdraw", REVISION_ID))
                .contentType(APPLICATION_JSON)
                .content(VALID_REQUEST))
            .andExpect(status().is(statusCode))
            .andExpect(jsonPath("$.code").value(code));
    }
}
