package io.regionevent.regioneventbackend.domain.content.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.service.SessionRevisionReviewDetailResult;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@ContentControllerWebMvcTest
class SessionRevisionReviewControllerIntegrationTest extends ContentControllerWebMvcTestSupport {

    private static final long REVISION_ID = 52L;

    @Test
    void 심사_대기_회차_수정_요청_상세를_명세_형식으로_반환한다() throws Exception {
        when(getSessionRevisionReviewDetailUseCase.get(AUTHENTICATED_USER_ID, REVISION_ID))
            .thenReturn(reviewDetailResult());

        mockMvc.perform(authenticated(get(
                "/api/v1/region-admin/session-revisions/{revisionId}",
                REVISION_ID
            )))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("심사 대기 회차 수정 요청 상세 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.revisionId").value("52"))
            .andExpect(jsonPath("$.data.contentId").value("10"))
            .andExpect(jsonPath("$.data.contentStatus").value("PUBLISHED"))
            .andExpect(jsonPath("$.data.targetSession.status").value("SCHEDULED"))
            .andExpect(jsonPath("$.data.targetSession.startsAt").value("2026-08-22T10:00:00+09:00"))
            .andExpect(jsonPath("$.data.candidate.startsAt").value("2026-08-29T10:00:00+09:00"))
            .andExpect(jsonPath("$.data.operator.operatorId").value("20"));

        verify(getSessionRevisionReviewDetailUseCase).get(AUTHENTICATED_USER_ID, REVISION_ID);
    }

    @Test
    void 수정_요청_ID가_양의_10진_문자열이_아니면_입력_오류로_응답한다() throws Exception {
        mockMvc.perform(authenticated(get("/api/v1/region-admin/session-revisions/0")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(authenticated(get("/api/v1/region-admin/session-revisions/not-a-number")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verify(getSessionRevisionReviewDetailUseCase, never()).get(eq(AUTHENTICATED_USER_ID), eq(REVISION_ID));
    }

    @Test
    void 인증이_없으면_유스케이스를_호출하지_않는다() throws Exception {
        mockMvc.perform(get("/api/v1/region-admin/session-revisions/{revisionId}", REVISION_ID))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verify(getSessionRevisionReviewDetailUseCase, never()).get(AUTHENTICATED_USER_ID, REVISION_ID);
    }

    @Test
    void 조회_대상과_권한_오류를_공통_오류_계약으로_응답한다() throws Exception {
        expectBusinessError(ErrorCode.NOT_FOUND, 404, "NOT_FOUND");
        expectBusinessError(ErrorCode.FORBIDDEN, 403, "FORBIDDEN");
    }

    private void expectBusinessError(
        ErrorCode errorCode,
        int statusCode,
        String code
    ) throws Exception {
        doThrow(new BusinessException(errorCode))
            .when(getSessionRevisionReviewDetailUseCase)
            .get(AUTHENTICATED_USER_ID, REVISION_ID);

        mockMvc.perform(authenticated(get(
                "/api/v1/region-admin/session-revisions/{revisionId}",
                REVISION_ID
            )))
            .andExpect(status().is(statusCode))
            .andExpect(jsonPath("$.code").value(code));
    }

    private SessionRevisionReviewDetailResult reviewDetailResult() {
        return new SessionRevisionReviewDetailResult(
            REVISION_ID,
            10L,
            "가야문화 체험",
            ContentStatus.PUBLISHED,
            new SessionRevisionReviewDetailResult.TargetSession(
                21L,
                ContentSessionStatus.SCHEDULED,
                3,
                Instant.parse("2026-08-22T01:00:00Z"),
                Instant.parse("2026-08-22T03:00:00Z"),
                Instant.parse("2026-08-22T00:30:00Z"),
                Instant.parse("2026-08-22T02:30:00Z"),
                30,
                30
            ),
            3,
            new SessionRevisionReviewDetailResult.Candidate(
                Instant.parse("2026-08-29T01:00:00Z"),
                Instant.parse("2026-08-29T03:00:00Z"),
                Instant.parse("2026-08-29T00:30:00Z"),
                Instant.parse("2026-08-29T02:30:00Z"),
                30
            ),
            Instant.parse("2026-08-01T01:00:00Z"),
            new SessionRevisionReviewDetailResult.Operator(20L, "김해운영")
        );
    }
}
