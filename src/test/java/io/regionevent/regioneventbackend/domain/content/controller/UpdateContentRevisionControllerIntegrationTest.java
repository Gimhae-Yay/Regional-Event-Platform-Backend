package io.regionevent.regioneventbackend.domain.content.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.content.dto.UpdateContentRevisionRequest;
import io.regionevent.regioneventbackend.domain.content.dto.UpdateContentRevisionResponse;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevisionStatus;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@ContentControllerWebMvcTest
class UpdateContentRevisionControllerIntegrationTest extends ContentControllerWebMvcTestSupport {

    private static final long REVISION_ID = 201L;
    private static final long CONTENT_ID = 200L;
    private static final String VALID_REQUEST = """
        {
          "title": "수정본 제목",
          "description": "수정본 설명입니다.",
          "locationText": "김해문화의전당",
          "operatingHoursText": "매일 10:00~18:00",
          "contactText": "055-123-4567",
          "precautions": "안내를 따라주세요.",
          "ageRequirement": "만 7세 이상",
          "materials": "편한 복장",
          "cancellationPolicyText": "시작 하루 전까지 취소할 수 있습니다.",
          "reservationPrice": 0,
          "publishAt": "2026-08-10T10:00:00+09:00",
          "representativeImageObjectId": "11"
        }
        """;

    @Test
    void 수정본_편집_유효한_요청이면_응답을_직렬화한다() throws Exception {
        when(updateContentRevisionUseCase.updateRevision(
            eq(AUTHENTICATED_USER_ID),
            eq(REVISION_ID),
            any(UpdateContentRevisionRequest.class)
        )).thenReturn(new UpdateContentRevisionResponse(
            Long.toString(REVISION_ID),
            Long.toString(CONTENT_ID),
            ContentRevisionStatus.EDIT_REJECTED
        ));

        mockMvc.perform(authenticated(put("/api/v1/operator/content-revisions/{revisionId}", REVISION_ID))
                .contentType(APPLICATION_JSON)
                .content(VALID_REQUEST))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("콘텐츠 수정본 편집에 성공했습니다."))
            .andExpect(jsonPath("$.data.revisionId").value(Long.toString(REVISION_ID)))
            .andExpect(jsonPath("$.data.contentId").value(Long.toString(CONTENT_ID)))
            .andExpect(jsonPath("$.data.status").value("EDIT_REJECTED"));

        verify(updateContentRevisionUseCase).updateRevision(
            eq(AUTHENTICATED_USER_ID),
            eq(REVISION_ID),
            any(UpdateContentRevisionRequest.class)
        );
    }

    @Test
    void 수정본_편집_인증이_없으면_유스케이스를_호출하지_않는다() throws Exception {
        mockMvc.perform(put("/api/v1/operator/content-revisions/{revisionId}", REVISION_ID)
                .contentType(APPLICATION_JSON)
                .content(VALID_REQUEST))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verify(updateContentRevisionUseCase, never()).updateRevision(any(), any(), any());
    }

    @Test
    void 수정본_편집_경로_ID가_양의_정수가_아니면_입력_오류를_반환한다() throws Exception {
        mockMvc.perform(authenticated(put("/api/v1/operator/content-revisions/0"))
                .contentType(APPLICATION_JSON)
                .content(VALID_REQUEST))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 수정본_편집_필수_본문이_비어있으면_입력_오류를_반환한다() throws Exception {
        mockMvc.perform(authenticated(put("/api/v1/operator/content-revisions/{revisionId}", REVISION_ID))
                .contentType(APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 수정본_편집_예약_가격이_누락되거나_음수면_입력_오류를_반환한다() throws Exception {
        String missingPriceRequest = VALID_REQUEST.replace("\"reservationPrice\": 0,", "");
        String negativePriceRequest = VALID_REQUEST.replace("\"reservationPrice\": 0", "\"reservationPrice\": -1");

        mockMvc.perform(authenticated(put("/api/v1/operator/content-revisions/{revisionId}", REVISION_ID))
                .contentType(APPLICATION_JSON)
                .content(missingPriceRequest))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(authenticated(put("/api/v1/operator/content-revisions/{revisionId}", REVISION_ID))
                .contentType(APPLICATION_JSON)
                .content(negativePriceRequest))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verify(updateContentRevisionUseCase, never()).updateRevision(any(), any(), any());
    }

    @Test
    void 수정본_편집_소유권_거절은_공통_오류로_응답한다() throws Exception {
        expectBusinessError(ErrorCode.FORBIDDEN, 403, "FORBIDDEN");
    }

    @Test
    void 수정본_편집_상태_충돌은_공통_오류로_응답한다() throws Exception {
        expectBusinessError(ErrorCode.CONTENT_STATE_CONFLICT, 409, "CONTENT_STATE_CONFLICT");
    }

    private void expectBusinessError(ErrorCode errorCode, int statusCode, String code) throws Exception {
        when(updateContentRevisionUseCase.updateRevision(
            eq(AUTHENTICATED_USER_ID),
            eq(REVISION_ID),
            any(UpdateContentRevisionRequest.class)
        )).thenThrow(new BusinessException(errorCode));

        mockMvc.perform(authenticated(put("/api/v1/operator/content-revisions/{revisionId}", REVISION_ID))
                .contentType(APPLICATION_JSON)
                .content(VALID_REQUEST))
            .andExpect(status().is(statusCode))
            .andExpect(jsonPath("$.code").value(code));
    }
}
