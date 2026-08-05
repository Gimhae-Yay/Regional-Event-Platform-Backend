package io.regionevent.regioneventbackend.domain.content.controller;

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
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.service.OriginalContentReviewDetailResult;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@ContentControllerWebMvcTest
class OriginalContentReviewDetailControllerIntegrationTest extends ContentControllerWebMvcTestSupport {

    private static final long CONTENT_ID = 200L;

    @Test
    void 원본_콘텐츠_심사_상세_조회_응답을_직렬화한다() throws Exception {
        when(getOriginalContentReviewDetailUseCase.get(AUTHENTICATED_USER_ID, CONTENT_ID))
            .thenReturn(reviewDetailResult());

        mockMvc.perform(authenticated(get("/api/v1/region-admin/contents/{contentId}", CONTENT_ID)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("승인 검토 콘텐츠 상세 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.contentId").value(Long.toString(CONTENT_ID)))
            .andExpect(jsonPath("$.data.regionId").value("10"))
            .andExpect(jsonPath("$.data.operatorId").value("20"))
            .andExpect(jsonPath("$.data.contentType").value("EVENT_EXPERIENCE"))
            .andExpect(jsonPath("$.data.status").value("PENDING"))
            .andExpect(jsonPath("$.data.sessions").isEmpty());

        verify(getOriginalContentReviewDetailUseCase).get(AUTHENTICATED_USER_ID, CONTENT_ID);
    }

    @Test
    void 원본_콘텐츠_심사_상세_조회_인증이_없으면_유스케이스를_호출하지_않는다() throws Exception {
        mockMvc.perform(get("/api/v1/region-admin/contents/{contentId}", CONTENT_ID))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verify(getOriginalContentReviewDetailUseCase, never()).get(AUTHENTICATED_USER_ID, CONTENT_ID);
    }

    @Test
    void 원본_콘텐츠_심사_상세_조회_경로_ID의_입력과_타입_오류를_구분한다() throws Exception {
        mockMvc.perform(authenticated(get("/api/v1/region-admin/contents/0")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        mockMvc.perform(authenticated(get("/api/v1/region-admin/contents/not-a-number")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));
    }

    @Test
    void 원본_콘텐츠_심사_상세_조회_비활성_사용자나_관할_불일치는_권한_오류로_응답한다() throws Exception {
        expectBusinessError(ErrorCode.FORBIDDEN, 403, "FORBIDDEN");
    }

    @Test
    void 원본_콘텐츠_심사_상세_조회_대상이_없으면_찾을수없음으로_응답한다() throws Exception {
        expectBusinessError(ErrorCode.NOT_FOUND, 404, "NOT_FOUND");
    }

    private void expectBusinessError(ErrorCode errorCode, int statusCode, String code) throws Exception {
        when(getOriginalContentReviewDetailUseCase.get(AUTHENTICATED_USER_ID, CONTENT_ID))
            .thenThrow(new BusinessException(errorCode));

        mockMvc.perform(authenticated(get("/api/v1/region-admin/contents/{contentId}", CONTENT_ID)))
            .andExpect(status().is(statusCode))
            .andExpect(jsonPath("$.code").value(code));
    }

    private OriginalContentReviewDetailResult reviewDetailResult() {
        return new OriginalContentReviewDetailResult(
            CONTENT_ID,
            10L,
            20L,
            ContentType.EVENT_EXPERIENCE,
            ContentStatus.PENDING,
            "김해 문화 체험",
            "가야 문화를 체험합니다.",
            "https://example.com/image",
            Instant.parse("2026-08-04T12:10:00Z"),
            "김해문화의전당",
            "매일 10:00~18:00",
            "055-123-4567",
            "안내를 따라주세요.",
            "만 7세 이상",
            "편한 복장",
            "하루 전 취소 가능",
            Instant.parse("2026-08-10T01:00:00Z"),
            List.of()
        );
    }
}
