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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.regionevent.regioneventbackend.domain.content.entity.ContentRevisionStatus;
import io.regionevent.regioneventbackend.domain.content.service.ResubmitContentRevisionResult;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@ContentControllerWebMvcTest
class ResubmitContentRevisionControllerIntegrationTest extends ContentControllerWebMvcTestSupport {

    private static final long SOURCE_REVISION_ID = 501L;
    private static final long REVISION_ID = 502L;
    private static final long CONTENT_ID = 101L;
    private static final Instant SUBMITTED_AT = Instant.parse("2026-08-21T01:00:00Z");

    @Test
    void 수정본_재제출_유효한_요청이면_계약_응답을_직렬화한다() throws Exception {
        when(resubmitContentRevisionUseCase.resubmit(AUTHENTICATED_USER_ID, SOURCE_REVISION_ID))
            .thenReturn(result());

        mockMvc.perform(authenticated(post(
                "/api/v1/operator/content-revisions/{revisionId}/resubmit",
                SOURCE_REVISION_ID
            )))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.statusCode").value(201))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("콘텐츠 수정본 재제출에 성공했습니다."))
            .andExpect(jsonPath("$.data.revisionId").value(Long.toString(REVISION_ID)))
            .andExpect(jsonPath("$.data.sourceRevisionId").value(Long.toString(SOURCE_REVISION_ID)))
            .andExpect(jsonPath("$.data.contentId").value(Long.toString(CONTENT_ID)))
            .andExpect(jsonPath("$.data.status").value("EDIT_REQUESTED"))
            .andExpect(jsonPath("$.data.baseContentVersion").value(3))
            .andExpect(jsonPath("$.data.submittedAt").value("2026-08-21T01:00:00Z"));

        verify(resubmitContentRevisionUseCase).resubmit(AUTHENTICATED_USER_ID, SOURCE_REVISION_ID);
    }

    @Test
    void 수정본_재제출_인증이_없으면_유스케이스를_호출하지_않는다() throws Exception {
        mockMvc.perform(post(
                "/api/v1/operator/content-revisions/{revisionId}/resubmit",
                SOURCE_REVISION_ID
            ))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verify(resubmitContentRevisionUseCase, never()).resubmit(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "9223372036854775808"})
    void 수정본_재제출_경로_ID가_유효하지_않으면_입력_오류를_반환한다(String revisionId)
        throws Exception {
        mockMvc.perform(authenticated(post(
                "/api/v1/operator/content-revisions/{revisionId}/resubmit",
                revisionId
            )))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verify(resubmitContentRevisionUseCase, never()).resubmit(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"FORBIDDEN", "NOT_FOUND", "CONTENT_STATE_CONFLICT"})
    void 수정본_재제출_업무_오류는_공통_오류로_응답한다(String errorCodeName) throws Exception {
        ErrorCode errorCode = ErrorCode.valueOf(errorCodeName);
        when(resubmitContentRevisionUseCase.resubmit(
            eq(AUTHENTICATED_USER_ID),
            eq(SOURCE_REVISION_ID)
        )).thenThrow(new BusinessException(errorCode));

        mockMvc.perform(authenticated(post(
                "/api/v1/operator/content-revisions/{revisionId}/resubmit",
                SOURCE_REVISION_ID
            )))
            .andExpect(status().is(errorCode.httpStatus().value()))
            .andExpect(jsonPath("$.code").value(errorCode.code()));
    }

    @Test
    void 수정본_재제출_이미지_정합성_오류는_내부_서버_오류로_응답한다() throws Exception {
        when(resubmitContentRevisionUseCase.resubmit(AUTHENTICATED_USER_ID, SOURCE_REVISION_ID))
            .thenThrow(new IllegalStateException("candidate image inconsistency"));

        mockMvc.perform(authenticated(post(
                "/api/v1/operator/content-revisions/{revisionId}/resubmit",
                SOURCE_REVISION_ID
            )))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));
    }

    private static ResubmitContentRevisionResult result() {
        return new ResubmitContentRevisionResult(
            REVISION_ID,
            SOURCE_REVISION_ID,
            CONTENT_ID,
            ContentRevisionStatus.EDIT_REQUESTED,
            3,
            SUBMITTED_AT
        );
    }
}
