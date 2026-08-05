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

import io.regionevent.regioneventbackend.domain.content.entity.ContentLogStatus;
import io.regionevent.regioneventbackend.domain.content.service.ContentHistoryResult;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@ContentControllerWebMvcTest
class ContentHistoryControllerIntegrationTest extends ContentControllerWebMvcTestSupport {

    private static final long CONTENT_ID = 200L;

    @Test
    void 콘텐츠_이력_조회_응답을_직렬화한다() throws Exception {
        when(getContentHistoryUseCase.get(AUTHENTICATED_USER_ID, CONTENT_ID))
            .thenReturn(new ContentHistoryResult(
                CONTENT_ID,
                List.of(new ContentHistoryResult.History(
                    ContentLogStatus.PENDING,
                    "승인 심사를 요청했습니다.",
                    Instant.parse("2026-08-04T12:00:00Z"),
                    new ContentHistoryResult.Actor(AUTHENTICATED_USER_ID, "지역 운영자")
                ))
            ));

        mockMvc.perform(authenticated(get("/api/v1/region-admin/contents/{contentId}/history", CONTENT_ID)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("콘텐츠 이력 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.contentId").value(CONTENT_ID))
            .andExpect(jsonPath("$.data.histories[0].status").value("PENDING"))
            .andExpect(jsonPath("$.data.histories[0].actor.userId").value(AUTHENTICATED_USER_ID))
            .andExpect(jsonPath("$.data.histories[0].actor.displayName").value("지역 운영자"));

        verify(getContentHistoryUseCase).get(AUTHENTICATED_USER_ID, CONTENT_ID);
    }

    @Test
    void 콘텐츠_이력_조회_인증이_없으면_유스케이스를_호출하지_않는다() throws Exception {
        mockMvc.perform(get("/api/v1/region-admin/contents/{contentId}/history", CONTENT_ID))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verify(getContentHistoryUseCase, never()).get(AUTHENTICATED_USER_ID, CONTENT_ID);
    }

    @Test
    void 콘텐츠_이력_조회_경로_ID의_입력과_타입_오류를_구분한다() throws Exception {
        mockMvc.perform(authenticated(get("/api/v1/region-admin/contents/0/history")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        mockMvc.perform(authenticated(get("/api/v1/region-admin/contents/not-a-number/history")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));
    }

    @Test
    void 콘텐츠_이력_조회_비활성_사용자나_관할_불일치는_권한_오류로_응답한다() throws Exception {
        expectBusinessError(ErrorCode.FORBIDDEN, 403, "FORBIDDEN");
    }

    @Test
    void 콘텐츠_이력_조회_대상이_없으면_찾을수없음으로_응답한다() throws Exception {
        expectBusinessError(ErrorCode.NOT_FOUND, 404, "NOT_FOUND");
    }

    private void expectBusinessError(ErrorCode errorCode, int statusCode, String code) throws Exception {
        when(getContentHistoryUseCase.get(AUTHENTICATED_USER_ID, CONTENT_ID))
            .thenThrow(new BusinessException(errorCode));

        mockMvc.perform(authenticated(get("/api/v1/region-admin/contents/{contentId}/history", CONTENT_ID)))
            .andExpect(status().is(statusCode))
            .andExpect(jsonPath("$.code").value(code));
    }
}
