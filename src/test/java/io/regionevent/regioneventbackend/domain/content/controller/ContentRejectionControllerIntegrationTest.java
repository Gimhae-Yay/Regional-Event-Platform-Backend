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

import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.service.RejectContentResult;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@ContentControllerWebMvcTest
class ContentRejectionControllerIntegrationTest extends ContentControllerWebMvcTestSupport {

    private static final long CONTENT_ID = 200L;
    private static final String REASON = "운영 정보 보완이 필요합니다.";
    private static final String VALID_REQUEST = "{\"reason\":\"" + REASON + "\"}";

    @Test
    void 콘텐츠_반려_유효한_요청이면_응답을_직렬화한다() throws Exception {
        when(rejectContentUseCase.reject(
            eq(AUTHENTICATED_USER_ID),
            eq(CONTENT_ID),
            eq(REASON),
            any(UUID.class)
        )).thenReturn(new RejectContentResult(
            CONTENT_ID,
            ContentStatus.REJECTED,
            Instant.parse("2026-08-04T12:00:00Z")
        ));

        mockMvc.perform(authenticated(post("/api/v1/region-admin/contents/{contentId}/reject", CONTENT_ID))
                .contentType(APPLICATION_JSON)
                .content(VALID_REQUEST))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("콘텐츠 반려에 성공했습니다."))
            .andExpect(jsonPath("$.data.contentId").value(CONTENT_ID))
            .andExpect(jsonPath("$.data.status").value("REJECTED"));

        verify(rejectContentUseCase).reject(
            eq(AUTHENTICATED_USER_ID),
            eq(CONTENT_ID),
            eq(REASON),
            any(UUID.class)
        );
    }

    @Test
    void 콘텐츠_반려_인증이_없으면_유스케이스를_호출하지_않는다() throws Exception {
        mockMvc.perform(post("/api/v1/region-admin/contents/{contentId}/reject", CONTENT_ID)
                .contentType(APPLICATION_JSON)
                .content(VALID_REQUEST))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verify(rejectContentUseCase, never()).reject(any(), any(), any(), any());
    }

    @Test
    void 콘텐츠_반려_사유가_공백이면_입력_오류를_반환한다() throws Exception {
        mockMvc.perform(authenticated(post("/api/v1/region-admin/contents/{contentId}/reject", CONTENT_ID))
                .contentType(APPLICATION_JSON)
                .content("{\"reason\":\" \"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 콘텐츠_반려_경로_ID의_입력과_타입_오류를_구분한다() throws Exception {
        mockMvc.perform(authenticated(post("/api/v1/region-admin/contents/0/reject"))
                .contentType(APPLICATION_JSON)
                .content(VALID_REQUEST))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        mockMvc.perform(authenticated(post("/api/v1/region-admin/contents/not-a-number/reject"))
                .contentType(APPLICATION_JSON)
                .content(VALID_REQUEST))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));
    }

    @Test
    void 콘텐츠_반려_역할이나_관할_불일치는_권한_오류로_응답한다() throws Exception {
        expectBusinessError(ErrorCode.FORBIDDEN, 403, "FORBIDDEN");
    }

    @Test
    void 콘텐츠_반려_상태_충돌은_공통_오류로_응답한다() throws Exception {
        expectBusinessError(ErrorCode.CONTENT_STATE_CONFLICT, 409, "CONTENT_STATE_CONFLICT");
    }

    private void expectBusinessError(ErrorCode errorCode, int statusCode, String code) throws Exception {
        when(rejectContentUseCase.reject(
            eq(AUTHENTICATED_USER_ID),
            eq(CONTENT_ID),
            eq(REASON),
            any(UUID.class)
        )).thenThrow(new BusinessException(errorCode));

        mockMvc.perform(authenticated(post("/api/v1/region-admin/contents/{contentId}/reject", CONTENT_ID))
                .contentType(APPLICATION_JSON)
                .content(VALID_REQUEST))
            .andExpect(status().is(statusCode))
            .andExpect(jsonPath("$.code").value(code));
    }
}
