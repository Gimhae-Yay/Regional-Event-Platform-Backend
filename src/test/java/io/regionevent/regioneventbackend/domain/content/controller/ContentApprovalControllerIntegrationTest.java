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
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.service.ApproveContentResult;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@ContentControllerWebMvcTest
class ContentApprovalControllerIntegrationTest extends ContentControllerWebMvcTestSupport {

    private static final long CONTENT_ID = 200L;

    @Test
    void 콘텐츠_승인_유효한_요청이면_응답을_직렬화한다() throws Exception {
        when(approveContentUseCase.approve(
            eq(AUTHENTICATED_USER_ID),
            eq(CONTENT_ID),
            any(UUID.class)
        )).thenReturn(new ApproveContentResult(
            CONTENT_ID,
            ContentStatus.APPROVED,
            Instant.parse("2026-08-10T01:00:00Z"),
            Instant.parse("2026-08-04T12:00:00Z")
        ));

        mockMvc.perform(authenticated(post("/api/v1/region-admin/contents/{contentId}/approve", CONTENT_ID)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("콘텐츠 승인에 성공했습니다."))
            .andExpect(jsonPath("$.data.contentId").value(CONTENT_ID))
            .andExpect(jsonPath("$.data.status").value("APPROVED"));

        verify(approveContentUseCase).approve(
            eq(AUTHENTICATED_USER_ID),
            eq(CONTENT_ID),
            any(UUID.class)
        );
    }

    @Test
    void 콘텐츠_승인_인증이_없으면_유스케이스를_호출하지_않는다() throws Exception {
        mockMvc.perform(post("/api/v1/region-admin/contents/{contentId}/approve", CONTENT_ID))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verify(approveContentUseCase, never()).approve(any(), any(), any());
    }

    @Test
    void 콘텐츠_승인_경로_ID의_입력과_타입_오류를_구분한다() throws Exception {
        mockMvc.perform(authenticated(post("/api/v1/region-admin/contents/0/approve")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        mockMvc.perform(authenticated(post("/api/v1/region-admin/contents/not-a-number/approve")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));
    }

    @Test
    void 콘텐츠_승인_비활성_사용자나_관할_불일치는_권한_오류로_응답한다() throws Exception {
        expectBusinessError(ErrorCode.FORBIDDEN, 403, "FORBIDDEN");
    }

    @Test
    void 콘텐츠_승인_대상이_없으면_찾을수없음으로_응답한다() throws Exception {
        expectBusinessError(ErrorCode.NOT_FOUND, 404, "NOT_FOUND");
    }

    @Test
    void 콘텐츠_승인_상태_충돌은_공통_오류로_응답한다() throws Exception {
        expectBusinessError(ErrorCode.CONTENT_STATE_CONFLICT, 409, "CONTENT_STATE_CONFLICT");
    }

    private void expectBusinessError(ErrorCode errorCode, int statusCode, String code) throws Exception {
        when(approveContentUseCase.approve(
            eq(AUTHENTICATED_USER_ID),
            eq(CONTENT_ID),
            any(UUID.class)
        )).thenThrow(new BusinessException(errorCode));

        mockMvc.perform(authenticated(post("/api/v1/region-admin/contents/{contentId}/approve", CONTENT_ID)))
            .andExpect(status().is(statusCode))
            .andExpect(jsonPath("$.code").value(code));
    }
}
