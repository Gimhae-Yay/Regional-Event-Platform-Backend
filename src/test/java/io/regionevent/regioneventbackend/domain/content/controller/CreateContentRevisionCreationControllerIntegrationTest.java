package io.regionevent.regioneventbackend.domain.content.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.content.dto.CreateContentRevisionRequest;
import io.regionevent.regioneventbackend.domain.content.dto.CreateContentRevisionResponse;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevisionStatus;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@ContentControllerWebMvcTest
class CreateContentRevisionCreationControllerIntegrationTest extends ContentControllerWebMvcTestSupport {

    private static final long CONTENT_ID = 200L;
    private static final long REVISION_ID = 201L;
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
          "publishAt": "2026-08-10T10:00:00+09:00",
          "representativeImageObjectId": "11"
        }
        """;

    @Test
    void 수정본_생성_유효한_인증과_요청이면_생성_응답을_직렬화한다() throws Exception {
        when(createContentRevisionUseCase.createRevision(
            eq(AUTHENTICATED_USER_ID),
            eq(CONTENT_ID),
            any(CreateContentRevisionRequest.class),
            anyString()
        )).thenReturn(new CreateContentRevisionResponse(
            Long.toString(REVISION_ID),
            Long.toString(CONTENT_ID),
            ContentRevisionStatus.EDIT_REQUESTED,
            1,
            Instant.parse("2026-08-04T12:00:00Z")
        ));

        mockMvc.perform(authenticated(post("/api/v1/operator/contents/{contentId}/revisions", CONTENT_ID))
                .contentType(APPLICATION_JSON)
                .content(VALID_REQUEST))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.statusCode").value(201))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("콘텐츠 수정본 생성과 승인 요청에 성공했습니다."))
            .andExpect(jsonPath("$.data.revisionId").value(Long.toString(REVISION_ID)))
            .andExpect(jsonPath("$.data.contentId").value(Long.toString(CONTENT_ID)))
            .andExpect(jsonPath("$.data.status").value("EDIT_REQUESTED"));

        verify(createContentRevisionUseCase).createRevision(
            eq(AUTHENTICATED_USER_ID),
            eq(CONTENT_ID),
            any(CreateContentRevisionRequest.class),
            anyString()
        );
    }

    @Test
    void 수정본_생성_인증이_없으면_유스케이스를_호출하지_않는다() throws Exception {
        mockMvc.perform(post("/api/v1/operator/contents/{contentId}/revisions", CONTENT_ID)
                .contentType(APPLICATION_JSON)
                .content(VALID_REQUEST))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verify(createContentRevisionUseCase, never()).createRevision(any(), any(), any(), any());
    }

    @Test
    void 수정본_생성_경로_ID가_양의_정수가_아니면_입력_오류를_반환한다() throws Exception {
        mockMvc.perform(authenticated(post("/api/v1/operator/contents/0/revisions"))
                .contentType(APPLICATION_JSON)
                .content(VALID_REQUEST))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 수정본_생성_필수_본문이_비어있으면_입력_오류를_반환한다() throws Exception {
        mockMvc.perform(authenticated(post("/api/v1/operator/contents/{contentId}/revisions", CONTENT_ID))
                .contentType(APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 수정본_생성_이미지_ID_타입_거절은_공통_오류로_응답한다() throws Exception {
        when(createContentRevisionUseCase.createRevision(
            eq(AUTHENTICATED_USER_ID),
            eq(CONTENT_ID),
            any(CreateContentRevisionRequest.class),
            anyString()
        )).thenThrow(new BusinessException(ErrorCode.INVALID_TYPE));

        mockMvc.perform(authenticated(post("/api/v1/operator/contents/{contentId}/revisions", CONTENT_ID))
                .contentType(APPLICATION_JSON)
                .content(VALID_REQUEST.replace("\"11\"", "11")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));
    }

    @Test
    void 수정본_생성_권한_거절은_공통_오류로_응답한다() throws Exception {
        expectBusinessError(ErrorCode.FORBIDDEN, 403, "FORBIDDEN");
    }

    @Test
    void 수정본_생성_대상이_없으면_공통_오류로_응답한다() throws Exception {
        expectBusinessError(ErrorCode.NOT_FOUND, 404, "NOT_FOUND");
    }

    @Test
    void 수정본_생성_상태가_충돌하면_공통_오류로_응답한다() throws Exception {
        expectBusinessError(ErrorCode.CONTENT_STATE_CONFLICT, 409, "CONTENT_STATE_CONFLICT");
    }

    private void expectBusinessError(ErrorCode errorCode, int statusCode, String code) throws Exception {
        when(createContentRevisionUseCase.createRevision(
            eq(AUTHENTICATED_USER_ID),
            eq(CONTENT_ID),
            any(CreateContentRevisionRequest.class),
            anyString()
        )).thenThrow(new BusinessException(errorCode));

        mockMvc.perform(authenticated(post("/api/v1/operator/contents/{contentId}/revisions", CONTENT_ID))
                .contentType(APPLICATION_JSON)
                .content(VALID_REQUEST))
            .andExpect(status().is(statusCode))
            .andExpect(jsonPath("$.code").value(code));
    }
}
