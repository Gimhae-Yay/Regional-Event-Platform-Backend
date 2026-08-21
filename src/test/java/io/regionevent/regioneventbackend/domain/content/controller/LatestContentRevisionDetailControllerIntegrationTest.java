package io.regionevent.regioneventbackend.domain.content.controller;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.content.entity.ContentRevisionStatus;
import io.regionevent.regioneventbackend.domain.content.service.LatestContentRevisionDetailResult;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@ContentControllerWebMvcTest
class LatestContentRevisionDetailControllerIntegrationTest extends ContentControllerWebMvcTestSupport {

    private static final long CONTENT_ID = 101L;
    private static final long REVISION_ID = 501L;

    @Test
    void 최신_콘텐츠_수정본_상세_조회_응답을_직렬화한다() throws Exception {
        when(getLatestContentRevisionUseCase.get(AUTHENTICATED_USER_ID, CONTENT_ID))
            .thenReturn(revisionDetailResult());

        mockMvc.perform(authenticated(get(
                "/api/v1/operator/contents/{contentId}/revisions/latest",
                CONTENT_ID
            )))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("내 최신 콘텐츠 수정본 상세 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.revisionId").value(Long.toString(REVISION_ID)))
            .andExpect(jsonPath("$.data.contentId").value(Long.toString(CONTENT_ID)))
            .andExpect(jsonPath("$.data.revisionNo").value(2))
            .andExpect(jsonPath("$.data.baseContentVersion").value(3))
            .andExpect(jsonPath("$.data.status").value("EDIT_REJECTED"))
            .andExpect(jsonPath("$.data.title").value("김해 가야문화 체험"))
            .andExpect(jsonPath("$.data.representativeImageUrl").value("https://example.com/image"))
            .andExpect(jsonPath("$.data.representativeImageUrlExpiresAt")
                .value("2026-08-18T04:00:00Z"))
            .andExpect(jsonPath("$.data.reservationPrice").value(20_000))
            .andExpect(jsonPath("$.data.reviewReason").value("후보 대표 이미지를 보완해 주세요."))
            .andExpect(jsonPath("$.data.submittedAt").value("2026-08-18T01:00:00Z"))
            .andExpect(jsonPath("$.data.reviewedAt").value("2026-08-18T03:00:00Z"))
            .andExpect(jsonPath("$.data.imageObjectId").doesNotExist())
            .andExpect(jsonPath("$.data.objectKey").doesNotExist());

        verify(getLatestContentRevisionUseCase).get(AUTHENTICATED_USER_ID, CONTENT_ID);
    }

    @Test
    void 최신_콘텐츠_수정본_상세_조회_인증이_없으면_유스케이스를_호출하지_않는다() throws Exception {
        mockMvc.perform(get(
                "/api/v1/operator/contents/{contentId}/revisions/latest",
                CONTENT_ID
            ))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verify(getLatestContentRevisionUseCase, never()).get(AUTHENTICATED_USER_ID, CONTENT_ID);
    }

    @Test
    void 최신_콘텐츠_수정본_상세_조회_경로_ID가_양의_Long이_아니면_입력_오류를_반환한다() throws Exception {
        assertInvalidContentId("0");
        assertInvalidContentId("not-a-number");
        assertInvalidContentId("9223372036854775808");
    }

    @Test
    void 최신_콘텐츠_수정본_상세_조회_유스케이스_오류를_계약대로_응답한다() throws Exception {
        assertBusinessError(ErrorCode.FORBIDDEN, 403, "FORBIDDEN");
        assertBusinessError(ErrorCode.NOT_FOUND, 404, "NOT_FOUND");
        assertBusinessError(ErrorCode.INTERNAL_SERVER_ERROR, 500, "INTERNAL_SERVER_ERROR");
    }

    private void assertInvalidContentId(String contentId) throws Exception {
        mockMvc.perform(authenticated(get(
                "/api/v1/operator/contents/{contentId}/revisions/latest",
                contentId
            )))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    private void assertBusinessError(ErrorCode errorCode, int statusCode, String code) throws Exception {
        doThrow(new BusinessException(errorCode))
            .when(getLatestContentRevisionUseCase)
            .get(AUTHENTICATED_USER_ID, CONTENT_ID);

        mockMvc.perform(authenticated(get(
                "/api/v1/operator/contents/{contentId}/revisions/latest",
                CONTENT_ID
            )))
            .andExpect(status().is(statusCode))
            .andExpect(jsonPath("$.code").value(code));
    }

    private LatestContentRevisionDetailResult revisionDetailResult() {
        return new LatestContentRevisionDetailResult(
            REVISION_ID,
            CONTENT_ID,
            2,
            3,
            ContentRevisionStatus.EDIT_REJECTED,
            "김해 가야문화 체험",
            "가야 문화를 체험하는 행사입니다.",
            "https://example.com/image",
            Instant.parse("2026-08-18T04:00:00Z"),
            "김해시 가야의길 190",
            "매주 토요일 10:00~16:00",
            "055-000-0000",
            "편한 복장으로 참여해 주세요.",
            "초등학생 이상",
            "필기도구",
            "회차 시작 전까지 취소할 수 있습니다.",
            20_000,
            null,
            "후보 대표 이미지를 보완해 주세요.",
            Instant.parse("2026-08-18T01:00:00Z"),
            Instant.parse("2026-08-18T03:00:00Z")
        );
    }
}
