package io.regionevent.regioneventbackend.domain.content.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.service.ContentRevisionReviewDetailResult;
import io.regionevent.regioneventbackend.domain.content.service.ContentRevisionReviewType;
import io.regionevent.regioneventbackend.domain.content.service.PendingContentRevisionListResult;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@ContentControllerWebMvcTest
class ContentRevisionReviewControllerIntegrationTest extends ContentControllerWebMvcTestSupport {

    private static final long REVISION_ID = 201L;
    private static final long CONTENT_ID = 200L;
    private static final String REQUESTED_STATUS = "EDIT_REQUESTED";

    @Test
    void 심사_대기_수정본_목록_조회_필터와_빈_응답을_직렬화한다() throws Exception {
        when(getPendingContentRevisionsUseCase.get(AUTHENTICATED_USER_ID, REQUESTED_STATUS))
            .thenReturn(new PendingContentRevisionListResult(List.of()));

        mockMvc.perform(authenticated(get("/api/v1/region-admin/content-revisions")
                .queryParam("status", REQUESTED_STATUS)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("담당 지역 심사 대기 수정본 목록 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.revisions").isEmpty());

        verify(getPendingContentRevisionsUseCase).get(AUTHENTICATED_USER_ID, REQUESTED_STATUS);
    }

    @Test
    void 심사_대기_수정본_목록_조회_인증이_없으면_유스케이스를_호출하지_않는다() throws Exception {
        mockMvc.perform(get("/api/v1/region-admin/content-revisions")
                .queryParam("status", REQUESTED_STATUS))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verify(getPendingContentRevisionsUseCase, never()).get(eq(AUTHENTICATED_USER_ID), eq(REQUESTED_STATUS));
    }

    @Test
    void 심사_대기_수정본_목록_조회_지원하지_않는_상태는_입력_오류로_응답한다() throws Exception {
        when(getPendingContentRevisionsUseCase.get(AUTHENTICATED_USER_ID, "EDIT_APPROVED"))
            .thenThrow(new BusinessException(ErrorCode.INVALID_INPUT));

        mockMvc.perform(authenticated(get("/api/v1/region-admin/content-revisions")
                .queryParam("status", "EDIT_APPROVED")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 심사_대기_수정본_목록_조회_관할_불일치는_권한_오류로_응답한다() throws Exception {
        when(getPendingContentRevisionsUseCase.get(AUTHENTICATED_USER_ID, REQUESTED_STATUS))
            .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        mockMvc.perform(authenticated(get("/api/v1/region-admin/content-revisions")
                .queryParam("status", REQUESTED_STATUS)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void 수정본_심사_상세_조회_응답을_직렬화한다() throws Exception {
        when(getContentRevisionReviewDetailUseCase.get(AUTHENTICATED_USER_ID, REVISION_ID))
            .thenReturn(reviewDetailResult());

        mockMvc.perform(authenticated(get(
                "/api/v1/region-admin/content-revisions/{revisionId}",
                REVISION_ID
            )))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("심사 대기 콘텐츠 수정본 상세 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.revisionId").value(Long.toString(REVISION_ID)))
            .andExpect(jsonPath("$.data.contentId").value(Long.toString(CONTENT_ID)))
            .andExpect(jsonPath("$.data.reviewType").value("PUBLISHED_REVISION"))
            .andExpect(jsonPath("$.data.contentStatus").value("PUBLISHED"))
            .andExpect(jsonPath("$.data.title").value("수정본 제목"))
            .andExpect(jsonPath("$.data.reservationPrice").value(15_000))
            .andExpect(jsonPath("$.data.sessions").isEmpty());

        verify(getContentRevisionReviewDetailUseCase).get(AUTHENTICATED_USER_ID, REVISION_ID);
    }

    @Test
    void 수정본_심사_상세_조회_인증이_없으면_유스케이스를_호출하지_않는다() throws Exception {
        mockMvc.perform(get("/api/v1/region-admin/content-revisions/{revisionId}", REVISION_ID))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verify(getContentRevisionReviewDetailUseCase, never()).get(AUTHENTICATED_USER_ID, REVISION_ID);
    }

    @Test
    void 수정본_심사_상세_조회_경로_ID의_입력과_타입_오류를_구분한다() throws Exception {
        mockMvc.perform(authenticated(get("/api/v1/region-admin/content-revisions/0")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        mockMvc.perform(authenticated(get("/api/v1/region-admin/content-revisions/not-a-number")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));
    }

    @Test
    void 수정본_심사_상세_조회_대상이_없으면_찾을수없음으로_응답한다() throws Exception {
        expectDetailBusinessError(ErrorCode.NOT_FOUND, 404, "NOT_FOUND");
    }

    @Test
    void 수정본_심사_상세_조회_관할_불일치는_권한_오류로_응답한다() throws Exception {
        expectDetailBusinessError(ErrorCode.FORBIDDEN, 403, "FORBIDDEN");
    }

    private void expectDetailBusinessError(ErrorCode errorCode, int statusCode, String code) throws Exception {
        when(getContentRevisionReviewDetailUseCase.get(AUTHENTICATED_USER_ID, REVISION_ID))
            .thenThrow(new BusinessException(errorCode));

        mockMvc.perform(authenticated(get(
                "/api/v1/region-admin/content-revisions/{revisionId}",
                REVISION_ID
            )))
            .andExpect(status().is(statusCode))
            .andExpect(jsonPath("$.code").value(code));
    }

    private ContentRevisionReviewDetailResult reviewDetailResult() {
        return new ContentRevisionReviewDetailResult(
            REVISION_ID,
            CONTENT_ID,
            ContentRevisionReviewType.PUBLISHED_REVISION,
            ContentStatus.PUBLISHED,
            "수정본 제목",
            "수정본 설명",
            "https://example.com/image",
            Instant.parse("2026-08-04T12:10:00Z"),
            "김해문화의전당",
            "매일 10:00~18:00",
            "055-123-4567",
            "안내를 따라주세요.",
            "만 7세 이상",
            "편한 복장",
            "하루 전 취소 가능",
            15_000,
            null,
            List.of(),
            Instant.parse("2026-08-04T12:00:00Z")
        );
    }
}
