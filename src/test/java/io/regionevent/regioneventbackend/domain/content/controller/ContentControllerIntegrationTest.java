package io.regionevent.regioneventbackend.domain.content.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.content.dto.CreateContentRequest;
import io.regionevent.regioneventbackend.domain.content.dto.CreateContentResponse;
import io.regionevent.regioneventbackend.domain.content.dto.UpdateMyContentRequest;
import io.regionevent.regioneventbackend.domain.content.dto.UpdateMyContentResponse;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@ContentControllerWebMvcTest
class ContentControllerIntegrationTest extends ContentControllerWebMvcTestSupport {

    private static final long CONTENT_ID = 200L;
    private static final String VALID_CREATE_REQUEST = """
        {
          "title": "김해 문화 체험",
          "description": "가야 문화를 체험합니다.",
          "locationText": "김해문화의전당",
          "operatingHoursText": "매일 10:00~18:00",
          "contactText": "055-123-4567",
          "precautions": "안내를 따라주세요.",
          "ageRequirement": "만 7세 이상",
          "materials": "편한 복장",
          "cancellationPolicyText": "시작 하루 전까지 취소할 수 있습니다.",
          "reservationPrice": 0,
          "publishAt": "2026-08-10T10:00:00+09:00",
          "representativeImageObjectId": "10",
          "sessions": [
            {
              "startsAt": "2026-08-11T10:00:00+09:00",
              "endsAt": "2026-08-11T12:00:00+09:00",
              "checkinOpenAt": "2026-08-11T09:30:00+09:00",
              "checkinCloseAt": "2026-08-11T11:30:00+09:00",
              "capacity": 20
            }
          ]
        }
        """;
    private static final String VALID_UPDATE_REQUEST = """
        {
          "title": "수정한 김해 문화 체험",
          "description": "수정한 설명입니다.",
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
    void 콘텐츠_생성_유효한_인증과_요청이면_생성_응답을_직렬화한다() throws Exception {
        when(createContentUseCase.createContent(eq(AUTHENTICATED_USER_ID), any(CreateContentRequest.class)))
            .thenReturn(new CreateContentResponse(
                Long.toString(CONTENT_ID),
                ContentType.EVENT_EXPERIENCE,
                ContentStatus.PENDING,
                Instant.parse("2026-08-04T12:00:00Z")
            ));

        mockMvc.perform(authenticated(post("/api/v1/operator/contents"))
                .contentType(APPLICATION_JSON)
                .content(VALID_CREATE_REQUEST))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.statusCode").value(201))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("콘텐츠 생성과 승인 요청에 성공했습니다."))
            .andExpect(jsonPath("$.data.contentId").value(Long.toString(CONTENT_ID)))
            .andExpect(jsonPath("$.data.contentType").value("EVENT_EXPERIENCE"))
            .andExpect(jsonPath("$.data.status").value("PENDING"));

        verify(createContentUseCase).createContent(eq(AUTHENTICATED_USER_ID), any(CreateContentRequest.class));
    }

    @Test
    void 콘텐츠_생성_인증이_없으면_유스케이스를_호출하지_않는다() throws Exception {
        mockMvc.perform(post("/api/v1/operator/contents")
                .contentType(APPLICATION_JSON)
                .content(VALID_CREATE_REQUEST))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verify(createContentUseCase, never()).createContent(any(), any());
    }

    @Test
    void 콘텐츠_생성_필수_본문이_비어있으면_입력_오류를_반환한다() throws Exception {
        mockMvc.perform(authenticated(post("/api/v1/operator/contents"))
                .contentType(APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verify(createContentUseCase, never()).createContent(any(), any());
    }

    @Test
    void 콘텐츠_생성_회차_정원_타입이_다르면_타입_오류를_반환한다() throws Exception {
        String invalidTypeRequest = VALID_CREATE_REQUEST.replace("\"capacity\": 20", "\"capacity\": \"twenty\"");

        mockMvc.perform(authenticated(post("/api/v1/operator/contents"))
                .contentType(APPLICATION_JSON)
                .content(invalidTypeRequest))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));
    }

    @Test
    void 콘텐츠_생성_권한_거절은_공통_오류로_응답한다() throws Exception {
        when(createContentUseCase.createContent(eq(AUTHENTICATED_USER_ID), any(CreateContentRequest.class)))
            .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        mockMvc.perform(authenticated(post("/api/v1/operator/contents"))
                .contentType(APPLICATION_JSON)
                .content(VALID_CREATE_REQUEST))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void 내_콘텐츠_수정_유효한_요청이면_응답을_직렬화한다() throws Exception {
        when(updateMyContentUseCase.updateContent(
            eq(AUTHENTICATED_USER_ID),
            eq(CONTENT_ID),
            any(UpdateMyContentRequest.class)
        )).thenReturn(new UpdateMyContentResponse(Long.toString(CONTENT_ID), ContentStatus.REJECTED));

        mockMvc.perform(authenticated(put("/api/v1/operator/contents/{contentId}", CONTENT_ID))
                .contentType(APPLICATION_JSON)
                .content(VALID_UPDATE_REQUEST))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("내 콘텐츠 수정에 성공했습니다."))
            .andExpect(jsonPath("$.data.contentId").value(Long.toString(CONTENT_ID)))
            .andExpect(jsonPath("$.data.status").value("REJECTED"));

        verify(updateMyContentUseCase).updateContent(
            eq(AUTHENTICATED_USER_ID),
            eq(CONTENT_ID),
            any(UpdateMyContentRequest.class)
        );
    }

    @Test
    void 내_콘텐츠_수정_인증이_없으면_유스케이스를_호출하지_않는다() throws Exception {
        mockMvc.perform(put("/api/v1/operator/contents/{contentId}", CONTENT_ID)
                .contentType(APPLICATION_JSON)
                .content(VALID_UPDATE_REQUEST))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verify(updateMyContentUseCase, never()).updateContent(any(), any(), any());
    }

    @Test
    void 내_콘텐츠_수정_경로_ID가_양의_정수가_아니면_입력_오류를_반환한다() throws Exception {
        mockMvc.perform(authenticated(put("/api/v1/operator/contents/0"))
                .contentType(APPLICATION_JSON)
                .content(VALID_UPDATE_REQUEST))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 내_콘텐츠_수정_필수_본문이_비어있으면_입력_오류를_반환한다() throws Exception {
        mockMvc.perform(authenticated(put("/api/v1/operator/contents/{contentId}", CONTENT_ID))
                .contentType(APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 내_콘텐츠_수정_소유권_거절은_공통_오류로_응답한다() throws Exception {
        when(updateMyContentUseCase.updateContent(
            eq(AUTHENTICATED_USER_ID),
            eq(CONTENT_ID),
            any(UpdateMyContentRequest.class)
        )).thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        mockMvc.perform(authenticated(put("/api/v1/operator/contents/{contentId}", CONTENT_ID))
                .contentType(APPLICATION_JSON)
                .content(VALID_UPDATE_REQUEST))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
