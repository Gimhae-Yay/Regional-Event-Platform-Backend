package io.regionevent.regioneventbackend.domain.content.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.service.PublicContentDetailResult;
import io.regionevent.regioneventbackend.domain.content.service.PublicContentListResult;
import io.regionevent.regioneventbackend.domain.content.service.PublicContentSearchCondition;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@ContentControllerWebMvcTest
class PublicContentControllerIntegrationTest extends ContentControllerWebMvcTestSupport {

    private static final long REGION_ID = 10L;
    private static final long CONTENT_ID = 200L;

    @Test
    void 공개_콘텐츠_목록_조회_필터와_응답을_직렬화한다() throws Exception {
        PublicContentSearchCondition condition = new PublicContentSearchCondition(
            REGION_ID,
            ContentType.EVENT_EXPERIENCE,
            true
        );
        when(getPublicContentsUseCase.get(condition)).thenReturn(new PublicContentListResult(List.of(
            new PublicContentListResult.Content(
                CONTENT_ID,
                ContentType.EVENT_EXPERIENCE,
                "김해 문화 체험",
                "김해문화의전당",
                "https://example.com/image",
                Instant.parse("2026-08-04T12:10:00Z"),
                true
            )
        )));

        mockMvc.perform(get("/api/v1/contents")
                .queryParam("regionId", Long.toString(REGION_ID))
                .queryParam("contentType", "EVENT_EXPERIENCE")
                .queryParam("reservationAvailable", "true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("공개 콘텐츠 목록 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.contents[0].contentId").value(Long.toString(CONTENT_ID)))
            .andExpect(jsonPath("$.data.contents[0].contentType").value("EVENT_EXPERIENCE"))
            .andExpect(jsonPath("$.data.contents[0].reservationAvailable").value(true));

        verify(getPublicContentsUseCase).get(condition);
    }

    @Test
    void 공개_콘텐츠_목록_조회_대상이_없으면_빈_목록을_반환한다() throws Exception {
        PublicContentSearchCondition condition = new PublicContentSearchCondition(REGION_ID, null, null);
        when(getPublicContentsUseCase.get(condition)).thenReturn(new PublicContentListResult(List.of()));

        mockMvc.perform(get("/api/v1/contents").queryParam("regionId", Long.toString(REGION_ID)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.contents").isEmpty());
    }

    @Test
    void 공개_콘텐츠_목록_조회_쿼리_입력_오류를_구분한다() throws Exception {
        mockMvc.perform(get("/api/v1/contents"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        mockMvc.perform(get("/api/v1/contents").queryParam("regionId", "0"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        mockMvc.perform(get("/api/v1/contents").queryParam("regionId", "not-a-number"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));

        mockMvc.perform(get("/api/v1/contents")
                .queryParam("regionId", Long.toString(REGION_ID))
                .queryParam("contentType", "NOT_A_TYPE"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 공개_콘텐츠_목록_조회_비공개_지역은_찾을수없음으로_응답한다() throws Exception {
        PublicContentSearchCondition condition = new PublicContentSearchCondition(REGION_ID, null, null);
        when(getPublicContentsUseCase.get(condition)).thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

        mockMvc.perform(get("/api/v1/contents").queryParam("regionId", Long.toString(REGION_ID)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void 공개_콘텐츠_상세_조회_응답을_직렬화한다() throws Exception {
        when(getPublicContentUseCase.get(CONTENT_ID)).thenReturn(contentDetailResult());

        mockMvc.perform(get("/api/v1/contents/{contentId}", CONTENT_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("공개 콘텐츠 상세 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.contentId").value(Long.toString(CONTENT_ID)))
            .andExpect(jsonPath("$.data.contentType").value("EVENT_EXPERIENCE"))
            .andExpect(jsonPath("$.data.title").value("김해 문화 체험"));

        verify(getPublicContentUseCase).get(CONTENT_ID);
    }

    @Test
    void 공개_콘텐츠_상세_조회_경로_ID가_양의_정수가_아니면_입력_오류를_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/contents/0"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        mockMvc.perform(get("/api/v1/contents/not-a-number"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 공개_콘텐츠_상세_조회_비공개_대상은_찾을수없음으로_응답한다() throws Exception {
        when(getPublicContentUseCase.get(CONTENT_ID)).thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

        mockMvc.perform(get("/api/v1/contents/{contentId}", CONTENT_ID))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    private PublicContentDetailResult contentDetailResult() {
        return new PublicContentDetailResult(
            CONTENT_ID,
            ContentType.EVENT_EXPERIENCE,
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
            "하루 전 취소 가능"
        );
    }
}
