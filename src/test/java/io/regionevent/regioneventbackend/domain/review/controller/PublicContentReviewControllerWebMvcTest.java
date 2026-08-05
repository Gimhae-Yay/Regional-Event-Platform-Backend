package io.regionevent.regioneventbackend.domain.review.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;

import io.regionevent.regioneventbackend.domain.review.service.PublicContentReviewListResult;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@WebMvcTest({PublicContentReviewController.class, VisitReviewController.class})
class PublicContentReviewControllerWebMvcTest extends ReviewControllerWebMvcTestSupport {

    @Test
    void getPublicContentReviews_공개후기존재_페이지응답을반환한다() throws Exception {
        Instant now = Instant.parse("2026-08-05T00:00:00Z");
        when(getPublicContentReviewsUseCase.get(1L, 0, 20)).thenReturn(new PublicContentReviewListResult(
            List.of(new PublicContentReviewListResult.Review(1L, "익명", 5, "좋은 행사입니다.", now, now)),
            0,
            20,
            1,
            1
        ));

        mockMvc.perform(get("/api/v1/contents/1/reviews"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("인증 후기 목록 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.content[0].reviewId").value("1"))
            .andExpect(jsonPath("$.data.content[0].authorDisplayName").value("익명"))
            .andExpect(jsonPath("$.data.page").value(0))
            .andExpect(jsonPath("$.data.totalElements").value(1));

        verify(getPublicContentReviewsUseCase).get(1L, 0, 20);
    }

    @Test
    void getPublicContentReviews_빈페이지_빈목록을반환한다() throws Exception {
        when(getPublicContentReviewsUseCase.get(1L, 1, 20))
            .thenReturn(new PublicContentReviewListResult(List.of(), 1, 20, 1, 1));

        mockMvc.perform(get("/api/v1/contents/1/reviews").param("page", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content").isEmpty())
            .andExpect(jsonPath("$.data.page").value(1));
    }

    @Test
    void getPublicContentReviews_경로와페이지입력이유효하지않음_계약된오류를응답한다() throws Exception {
        mockMvc.perform(get("/api/v1/contents/0/reviews"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(get("/api/v1/contents/not-a-number/reviews"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));
        mockMvc.perform(get("/api/v1/contents/1/reviews").param("page", "-1"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(get("/api/v1/contents/1/reviews").param("size", "101"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(getPublicContentReviewsUseCase);
    }

    @Test
    void getPublicContentReviews_비공개콘텐츠_찾을수없음오류를응답한다() throws Exception {
        when(getPublicContentReviewsUseCase.get(1L, 0, 20)).thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

        mockMvc.perform(get("/api/v1/contents/1/reviews"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
