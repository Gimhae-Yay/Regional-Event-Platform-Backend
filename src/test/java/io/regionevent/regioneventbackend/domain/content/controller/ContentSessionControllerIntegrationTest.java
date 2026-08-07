package io.regionevent.regioneventbackend.domain.content.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.content.service.PublicSessionReservationInfo;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@ContentControllerWebMvcTest
class ContentSessionControllerIntegrationTest extends ContentControllerWebMvcTestSupport {

    private static final long SESSION_ID = 200L;
    private static final long CONTENT_ID = 300L;
    private static final Instant STARTS_AT = Instant.parse("2026-08-05T01:00:00Z");
    private static final Instant ENDS_AT = Instant.parse("2026-08-05T03:00:00Z");

    @Test
    void 회차_예약정보_조회_공개_예정_회차의_HTTP_응답을_직렬화한다() throws Exception {
        when(contentSessionService.findPublicScheduledReservationInfo(SESSION_ID))
            .thenReturn(new PublicSessionReservationInfo(
                SESSION_ID,
                CONTENT_ID,
                STARTS_AT,
                ENDS_AT,
                2,
                true
            ));

        mockMvc.perform(get("/api/v1/sessions/{sessionId}", SESSION_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("회차 예약 정보 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.sessionId").value(Long.toString(SESSION_ID)))
            .andExpect(jsonPath("$.data.contentId").value(Long.toString(CONTENT_ID)))
            .andExpect(jsonPath("$.data.startsAt").value("2026-08-05T10:00:00+09:00"))
            .andExpect(jsonPath("$.data.endsAt").value("2026-08-05T12:00:00+09:00"))
            .andExpect(jsonPath("$.data.price").value(0))
            .andExpect(jsonPath("$.data.remainingCapacity").value(2))
            .andExpect(jsonPath("$.data.reservable").value(true));

        verify(contentSessionService).findPublicScheduledReservationInfo(SESSION_ID);
    }

    @Test
    void 회차_예약정보_조회_잔여_정원이_없으면_예약_불가를_직렬화한다() throws Exception {
        when(contentSessionService.findPublicScheduledReservationInfo(SESSION_ID))
            .thenReturn(new PublicSessionReservationInfo(
                SESSION_ID,
                CONTENT_ID,
                STARTS_AT,
                ENDS_AT,
                0,
                true
            ));

        mockMvc.perform(get("/api/v1/sessions/{sessionId}", SESSION_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.remainingCapacity").value(0))
            .andExpect(jsonPath("$.data.reservable").value(false));
    }

    @Test
    void 회차_예약정보_조회_업무_실패는_공통_오류로_응답한다() throws Exception {
        when(contentSessionService.findPublicScheduledReservationInfo(SESSION_ID))
            .thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

        mockMvc.perform(get("/api/v1/sessions/{sessionId}", SESSION_ID))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void 회차_예약정보_조회_식별자_경계의_입력_오류를_구분한다() throws Exception {
        mockMvc.perform(get("/api/v1/sessions/0"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        mockMvc.perform(get("/api/v1/sessions/not-a-number"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));
    }

    @Test
    void 공개_콘텐츠_회차_목록_조회_인증없이_빈_목록을_직렬화한다() throws Exception {
        when(getPublicContentSessionsUseCase.get(CONTENT_ID)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/contents/{contentId}/sessions", CONTENT_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("콘텐츠 회차 목록 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.contentId").value(Long.toString(CONTENT_ID)))
            .andExpect(jsonPath("$.data.sessions").isEmpty());

        verify(getPublicContentSessionsUseCase).get(CONTENT_ID);
    }

    @Test
    void 공개_콘텐츠_회차_목록_조회_업무_실패는_공통_오류로_응답한다() throws Exception {
        when(getPublicContentSessionsUseCase.get(CONTENT_ID))
            .thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

        mockMvc.perform(get("/api/v1/contents/{contentId}/sessions", CONTENT_ID))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void 공개_콘텐츠_회차_목록_조회_식별자_입력_오류를_구분한다() throws Exception {
        mockMvc.perform(get("/api/v1/contents/0/sessions"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        mockMvc.perform(get("/api/v1/contents/not-a-number/sessions"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));
    }

    @Test
    void MVC_slice는_DB_인프라를_초기화하지_않는다() {
        assertThat(hasDatabaseInfrastructure()).isFalse();
    }
}
