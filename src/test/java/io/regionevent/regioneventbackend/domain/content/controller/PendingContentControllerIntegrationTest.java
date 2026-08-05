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
import io.regionevent.regioneventbackend.domain.content.service.PendingContentListResult;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@ContentControllerWebMvcTest
class PendingContentControllerIntegrationTest extends ContentControllerWebMvcTestSupport {

    @Test
    void 담당지역_원본_심사대기_콘텐츠_목록을_응답계약에_맞춰_직렬화한다() throws Exception {
        when(getPendingContentsUseCase.get(AUTHENTICATED_USER_ID, "PENDING"))
            .thenReturn(pendingContents());

        mockMvc.perform(authenticated(get("/api/v1/region-admin/contents")
                .queryParam("status", "PENDING")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("담당 지역 승인 대기 콘텐츠 목록 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.contents[0].contentId").value("101"))
            .andExpect(jsonPath("$.data.contents[0].contentType").value("EVENT_EXPERIENCE"))
            .andExpect(jsonPath("$.data.contents[0].status").value("PENDING"))
            .andExpect(jsonPath("$.data.contents[0].publishAt").value("2026-08-10T09:00:00+09:00"))
            .andExpect(jsonPath("$.data.contents[0].submittedAt").value("2026-08-01T00:00:00Z"))
            .andExpect(jsonPath("$.data.contents[0].operator.operatorId").value("20"))
            .andExpect(jsonPath("$.data.contents[0].representativeImageUrl")
                .value("https://example.invalid/view/101"))
            .andExpect(jsonPath("$.data.contents[0].imageObjectId").doesNotExist())
            .andExpect(jsonPath("$.data.contents[0].representativeImageObjectId").doesNotExist());

        verify(getPendingContentsUseCase).get(AUTHENTICATED_USER_ID, "PENDING");
    }

    @Test
    void 인증이_없으면_유스케이스를_호출하지_않는다() throws Exception {
        mockMvc.perform(get("/api/v1/region-admin/contents").queryParam("status", "PENDING"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verify(getPendingContentsUseCase, never()).get(AUTHENTICATED_USER_ID, "PENDING");
    }

    @Test
    void status_누락과_잘못된_값은_입력오류로_응답한다() throws Exception {
        when(getPendingContentsUseCase.get(AUTHENTICATED_USER_ID, null))
            .thenThrow(new BusinessException(ErrorCode.INVALID_INPUT));
        when(getPendingContentsUseCase.get(AUTHENTICATED_USER_ID, "APPROVED"))
            .thenThrow(new BusinessException(ErrorCode.INVALID_INPUT));

        mockMvc.perform(authenticated(get("/api/v1/region-admin/contents")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(authenticated(get("/api/v1/region-admin/contents")
                .queryParam("status", "APPROVED")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 담당지역_관리자가_아니면_권한오류로_응답한다() throws Exception {
        when(getPendingContentsUseCase.get(AUTHENTICATED_USER_ID, "PENDING"))
            .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        mockMvc.perform(authenticated(get("/api/v1/region-admin/contents")
                .queryParam("status", "PENDING")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    private PendingContentListResult pendingContents() {
        return new PendingContentListResult(List.of(new PendingContentListResult.Content(
            101L,
            ContentType.EVENT_EXPERIENCE,
            "김해 가야문화 체험",
            ContentStatus.PENDING,
            Instant.parse("2026-08-10T00:00:00Z"),
            Instant.parse("2026-08-01T00:00:00Z"),
            20L,
            "김운영",
            "https://example.invalid/view/101",
            Instant.parse("2026-08-01T00:05:00Z")
        )));
    }
}
