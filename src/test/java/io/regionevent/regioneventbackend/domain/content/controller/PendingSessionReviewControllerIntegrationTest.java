package io.regionevent.regioneventbackend.domain.content.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.content.service.PendingSessionReviewDetailResult;

@ContentControllerWebMvcTest
class PendingSessionReviewControllerIntegrationTest extends ContentControllerWebMvcTestSupport {

    private static final long SESSION_ID = 200L;

    @Test
    void 심사_대기_회차_상세_조회_일정은_서울_오프셋으로_직렬화한다() throws Exception {
        when(getPendingSessionReviewDetailUseCase.get(AUTHENTICATED_USER_ID, SESSION_ID))
            .thenReturn(reviewDetailResult());

        mockMvc.perform(authenticated(get("/api/v1/region-admin/sessions/{sessionId}", SESSION_ID)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.startsAt").value("2026-08-05T10:00:00+09:00"))
            .andExpect(jsonPath("$.data.endsAt").value("2026-08-05T12:00:00+09:00"))
            .andExpect(jsonPath("$.data.checkinOpenAt").value("2026-08-05T09:30:00+09:00"))
            .andExpect(jsonPath("$.data.checkinCloseAt").value("2026-08-05T11:30:00+09:00"));

        verify(getPendingSessionReviewDetailUseCase).get(AUTHENTICATED_USER_ID, SESSION_ID);
    }

    private PendingSessionReviewDetailResult reviewDetailResult() {
        return new PendingSessionReviewDetailResult(
            SESSION_ID,
            300L,
            "김해 문화 체험",
            "APPROVED",
            "PENDING",
            Instant.parse("2026-08-05T01:00:00Z"),
            Instant.parse("2026-08-05T03:00:00Z"),
            Instant.parse("2026-08-05T00:30:00Z"),
            Instant.parse("2026-08-05T02:30:00Z"),
            20,
            20,
            Instant.parse("2026-08-01T01:00:00Z"),
            400L,
            "운영자"
        );
    }
}
