package io.regionevent.regioneventbackend.domain.review.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;

import io.regionevent.regioneventbackend.domain.review.dto.CreateVisitReviewResponse;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@WebMvcTest({PublicContentReviewController.class, VisitReviewController.class})
class VisitReviewControllerWebMvcTest extends ReviewControllerWebMvcTestSupport {

    private static final long USER_ID = 100L;

    @Test
    void createReview_유효한요청_후기작성응답을반환한다() throws Exception {
        when(createVisitReviewUseCase.create(eq(USER_ID), eq(1L), any(), any())).thenReturn(new CreateVisitReviewResponse(
            "1", "1", "10", 5, "좋은 행사입니다.", Instant.parse("2026-08-05T00:00:00Z")
        ));

        mockMvc.perform(authenticated(post("/api/v1/visits/1/reviews"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":5,\"reviewText\":\"좋은 행사입니다.\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.statusCode").value(201))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("후기 작성에 성공했습니다."))
            .andExpect(jsonPath("$.data.reviewId").value("1"))
            .andExpect(jsonPath("$.data.rating").value(5))
            .andExpect(jsonPath("$.data.reviewText").value("좋은 행사입니다."));

        verify(createVisitReviewUseCase).create(eq(USER_ID), eq(1L), any(), any());
    }

    @Test
    void createReview_식별자또는본문이유효하지않음_계약된입력오류를응답하고유스케이스를호출하지않는다() throws Exception {
        mockMvc.perform(authenticated(post("/api/v1/visits/0/reviews"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":5,\"reviewText\":\"후기\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(authenticated(post("/api/v1/visits/1/reviews"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":\"5\",\"reviewText\":\"후기\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));
        mockMvc.perform(authenticated(post("/api/v1/visits/1/reviews"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":0,\"reviewText\":\" \"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(createVisitReviewUseCase);
    }

    @Test
    void createReview_후기작성불가_충돌오류를응답한다() throws Exception {
        when(createVisitReviewUseCase.create(eq(USER_ID), eq(1L), any(), any()))
            .thenThrow(new BusinessException(ErrorCode.CHECK_IN_CONFLICT));

        mockMvc.perform(authenticated(post("/api/v1/visits/1/reviews"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":5,\"reviewText\":\"후기\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CHECK_IN_CONFLICT"));
    }

    @Test
    void createReview_인증정보없음_미인증오류를응답한다() throws Exception {
        mockMvc.perform(post("/api/v1/visits/1/reviews")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":5,\"reviewText\":\"후기\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

}
