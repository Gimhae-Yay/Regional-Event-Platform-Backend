package io.regionevent.regioneventbackend.domain.content.controller;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.service.MyContentDetailResult;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@ContentControllerWebMvcTest
class MyContentDetailControllerIntegrationTest extends ContentControllerWebMvcTestSupport {

    private static final long CONTENT_ID = 200L;

    @Test
    void 내_콘텐츠_상세_조회_응답을_직렬화한다() throws Exception {
        when(getMyContentUseCase.get(AUTHENTICATED_USER_ID, CONTENT_ID))
            .thenReturn(contentDetailResult());

        mockMvc.perform(authenticated(get("/api/v1/operator/contents/{contentId}", CONTENT_ID)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("내 콘텐츠 상세 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.contentId").value(Long.toString(CONTENT_ID)))
            .andExpect(jsonPath("$.data.contentType").value("EVENT_EXPERIENCE"))
            .andExpect(jsonPath("$.data.status").value("REJECTED"))
            .andExpect(jsonPath("$.data.title").value("김해 문화 체험"))
            .andExpect(jsonPath("$.data.publishAt").value("2026-08-10T10:00:00+09:00"));

        verify(getMyContentUseCase).get(AUTHENTICATED_USER_ID, CONTENT_ID);
    }

    @Test
    void 내_콘텐츠_상세_조회_이전_경로도_동일한_응답을_반환한다() throws Exception {
        when(getMyContentUseCase.get(AUTHENTICATED_USER_ID, CONTENT_ID))
            .thenReturn(contentDetailResult());

        mockMvc.perform(authenticated(get("/operator/contents/{contentId}", CONTENT_ID)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.contentId").value(Long.toString(CONTENT_ID)));
    }

    @Test
    void 내_콘텐츠_상세_조회_인증이_없으면_유스케이스를_호출하지_않는다() throws Exception {
        mockMvc.perform(get("/api/v1/operator/contents/{contentId}", CONTENT_ID))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verify(getMyContentUseCase, never()).get(AUTHENTICATED_USER_ID, CONTENT_ID);
    }

    @Test
    void 내_콘텐츠_상세_조회_경로_ID가_양의_정수가_아니면_입력_오류를_반환한다() throws Exception {
        mockMvc.perform(authenticated(get("/api/v1/operator/contents/0")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 내_콘텐츠_상세_조회_소유권_불일치는_권한_오류로_응답한다() throws Exception {
        expectBusinessError(ErrorCode.FORBIDDEN, 403, "FORBIDDEN");
    }

    @Test
    void 내_콘텐츠_상세_조회_대상이_없으면_찾을수없음으로_응답한다() throws Exception {
        expectBusinessError(ErrorCode.NOT_FOUND, 404, "NOT_FOUND");
    }

    private void expectBusinessError(ErrorCode errorCode, int statusCode, String code) throws Exception {
        when(getMyContentUseCase.get(AUTHENTICATED_USER_ID, CONTENT_ID))
            .thenThrow(new BusinessException(errorCode));

        mockMvc.perform(authenticated(get("/api/v1/operator/contents/{contentId}", CONTENT_ID)))
            .andExpect(status().is(statusCode))
            .andExpect(jsonPath("$.code").value(code));
    }

    private MyContentDetailResult contentDetailResult() {
        return new MyContentDetailResult(
            CONTENT_ID,
            ContentType.EVENT_EXPERIENCE,
            ContentStatus.REJECTED,
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
            "운영 정보 보완이 필요합니다.",
            Instant.parse("2026-08-01T01:00:00Z"),
            Instant.parse("2026-08-04T12:00:00Z")
        );
    }
}
