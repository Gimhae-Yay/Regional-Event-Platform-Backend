package io.regionevent.regioneventbackend.domain.content.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.content.entity.ContentLogStatus;
import io.regionevent.regioneventbackend.domain.content.service.DeleteContentResult;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@ContentControllerWebMvcTest
class ContentDeletionControllerIntegrationTest extends ContentControllerWebMvcTestSupport {

    private static final long CONTENT_ID = 200L;
    private static final String REASON = "중복 등록된 콘텐츠입니다.";
    private static final String VALID_REQUEST = "{\"reason\":\"" + REASON + "\"}";

    @Test
    void 콘텐츠_삭제_유효한_요청이면_삭제_응답을_직렬화한다() throws Exception {
        when(deleteContentUseCase.delete(
            eq(AUTHENTICATED_USER_ID),
            eq(CONTENT_ID),
            eq(REASON),
            any(UUID.class)
        )).thenReturn(new DeleteContentResult(
            CONTENT_ID,
            ContentLogStatus.DELETED,
            Instant.parse("2026-08-04T12:00:00Z"),
            REASON
        ));

        mockMvc.perform(authenticated(delete("/api/v1/region-admin/contents/{contentId}", CONTENT_ID))
                .contentType(APPLICATION_JSON)
                .content(VALID_REQUEST))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("공개 전 콘텐츠 삭제에 성공했습니다."))
            .andExpect(jsonPath("$.data.contentId").value(Long.toString(CONTENT_ID)))
            .andExpect(jsonPath("$.data.deletionEventStatus").value("DELETED"))
            .andExpect(jsonPath("$.data.deletionReason").value(REASON));

        verify(deleteContentUseCase).delete(
            eq(AUTHENTICATED_USER_ID),
            eq(CONTENT_ID),
            eq(REASON),
            any(UUID.class)
        );
    }

    @Test
    void 콘텐츠_삭제_인증이_없으면_유스케이스를_호출하지_않는다() throws Exception {
        mockMvc.perform(delete("/api/v1/region-admin/contents/{contentId}", CONTENT_ID)
                .contentType(APPLICATION_JSON)
                .content(VALID_REQUEST))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verify(deleteContentUseCase, never()).delete(any(), any(), any(), any());
    }

    @Test
    void 콘텐츠_삭제_사유가_공백이면_입력_오류를_반환한다() throws Exception {
        mockMvc.perform(authenticated(delete("/api/v1/region-admin/contents/{contentId}", CONTENT_ID))
                .contentType(APPLICATION_JSON)
                .content("{\"reason\":\" \"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 콘텐츠_삭제_경로_ID의_입력과_타입_오류를_구분한다() throws Exception {
        mockMvc.perform(authenticated(delete("/api/v1/region-admin/contents/0"))
                .contentType(APPLICATION_JSON)
                .content(VALID_REQUEST))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        mockMvc.perform(authenticated(delete("/api/v1/region-admin/contents/not-a-number"))
                .contentType(APPLICATION_JSON)
                .content(VALID_REQUEST))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));
    }

    @Test
    void 콘텐츠_삭제_관할_거절은_공통_오류로_응답한다() throws Exception {
        expectBusinessError(ErrorCode.FORBIDDEN, 403, "FORBIDDEN");
    }

    @Test
    void 콘텐츠_삭제_상태_충돌은_공통_오류로_응답한다() throws Exception {
        expectBusinessError(ErrorCode.CONTENT_STATE_CONFLICT, 409, "CONTENT_STATE_CONFLICT");
    }

    private void expectBusinessError(ErrorCode errorCode, int statusCode, String code) throws Exception {
        when(deleteContentUseCase.delete(
            eq(AUTHENTICATED_USER_ID),
            eq(CONTENT_ID),
            eq(REASON),
            any(UUID.class)
        )).thenThrow(new BusinessException(errorCode));

        mockMvc.perform(authenticated(delete("/api/v1/region-admin/contents/{contentId}", CONTENT_ID))
                .contentType(APPLICATION_JSON)
                .content(VALID_REQUEST))
            .andExpect(status().is(statusCode))
            .andExpect(jsonPath("$.code").value(code));
    }
}
