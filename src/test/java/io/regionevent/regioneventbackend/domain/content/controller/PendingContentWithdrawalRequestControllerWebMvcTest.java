package io.regionevent.regioneventbackend.domain.content.controller;

import static org.hamcrest.Matchers.nullValue;
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
import io.regionevent.regioneventbackend.domain.content.service.PendingContentWithdrawalRequestListResult;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@ContentControllerWebMvcTest
class PendingContentWithdrawalRequestControllerWebMvcTest
    extends ContentControllerWebMvcTestSupport {

    private static final String PATH = "/api/v1/region-admin/content-withdrawal-requests";

    @Test
    void 대기_요청을_정확한_성공_계약과_UTC_시각으로_직렬화한다() throws Exception {
        when(getPendingContentWithdrawalRequestsUseCase.get(
            AUTHENTICATED_USER_ID,
            "PENDING"
        )).thenReturn(pendingRequests(false));

        mockMvc.perform(authenticated(get(PATH).queryParam("status", "PENDING")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message")
                .value("전체 콘텐츠 철회 요청 대기 목록 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.withdrawalRequests[0].withdrawalRequestId")
                .value("7001"))
            .andExpect(jsonPath("$.data.withdrawalRequests[0].contentId").value("101"))
            .andExpect(jsonPath("$.data.withdrawalRequests[0].contentType")
                .value("EVENT_EXPERIENCE"))
            .andExpect(jsonPath("$.data.withdrawalRequests[0].contentTitle")
                .value("김해 가야문화 체험"))
            .andExpect(jsonPath("$.data.withdrawalRequests[0].contentStatus")
                .value("PUBLISHED"))
            .andExpect(jsonPath("$.data.withdrawalRequests[0].requester.userId").value("20"))
            .andExpect(jsonPath("$.data.withdrawalRequests[0].requester.name").value("김운영"))
            .andExpect(jsonPath("$.data.withdrawalRequests[0].requestedAt")
                .value("2026-08-16T04:00:00Z"))
            .andExpect(jsonPath("$.data.withdrawalRequests[0].requestReason").doesNotExist())
            .andExpect(jsonPath("$.data.withdrawalRequests[0].idempotencyKeyHash").doesNotExist())
            .andExpect(jsonPath("$.data.withdrawalRequests[0].reviewedAt").doesNotExist())
            .andExpect(jsonPath("$.data.withdrawalRequests[0].invalidatedAt").doesNotExist());
    }

    @Test
    void 요청자_연결이_없으면_requester를_null로_직렬화한다() throws Exception {
        when(getPendingContentWithdrawalRequestsUseCase.get(
            AUTHENTICATED_USER_ID,
            "PENDING"
        )).thenReturn(pendingRequests(true));

        mockMvc.perform(authenticated(get(PATH).queryParam("status", "PENDING")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.withdrawalRequests[0].requester").value(nullValue()));
    }

    @Test
    void 빈_결과는_빈_목록으로_응답한다() throws Exception {
        when(getPendingContentWithdrawalRequestsUseCase.get(
            AUTHENTICATED_USER_ID,
            "PENDING"
        )).thenReturn(new PendingContentWithdrawalRequestListResult(List.of()));

        mockMvc.perform(authenticated(get(PATH).queryParam("status", "PENDING")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.withdrawalRequests").isEmpty());
    }

    @Test
    void 인증이_없으면_유스케이스에_진입하지_않는다() throws Exception {
        mockMvc.perform(get(PATH).queryParam("status", "PENDING"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verify(getPendingContentWithdrawalRequestsUseCase, never()).get(
            AUTHENTICATED_USER_ID,
            "PENDING"
        );
    }

    @Test
    void status_누락_빈값_지원하지_않는_값은_입력오류로_응답한다() throws Exception {
        when(getPendingContentWithdrawalRequestsUseCase.get(AUTHENTICATED_USER_ID, null))
            .thenThrow(new BusinessException(ErrorCode.INVALID_INPUT));
        when(getPendingContentWithdrawalRequestsUseCase.get(AUTHENTICATED_USER_ID, ""))
            .thenThrow(new BusinessException(ErrorCode.INVALID_INPUT));
        when(getPendingContentWithdrawalRequestsUseCase.get(
            AUTHENTICATED_USER_ID,
            "APPROVED"
        )).thenThrow(new BusinessException(ErrorCode.INVALID_INPUT));

        mockMvc.perform(authenticated(get(PATH)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(authenticated(get(PATH).queryParam("status", "")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(authenticated(get(PATH).queryParam("status", "APPROVED")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 권한이나_담당지역이_없으면_권한오류로_응답한다() throws Exception {
        when(getPendingContentWithdrawalRequestsUseCase.get(
            AUTHENTICATED_USER_ID,
            "PENDING"
        )).thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        mockMvc.perform(authenticated(get(PATH).queryParam("status", "PENDING")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    private PendingContentWithdrawalRequestListResult pendingRequests(
        boolean requesterUnlinked
    ) {
        PendingContentWithdrawalRequestListResult.Requester requester = requesterUnlinked
            ? null
            : new PendingContentWithdrawalRequestListResult.Requester(20L, "김운영");
        return new PendingContentWithdrawalRequestListResult(List.of(
            new PendingContentWithdrawalRequestListResult.WithdrawalRequest(
                7001L,
                101L,
                ContentType.EVENT_EXPERIENCE,
                "김해 가야문화 체험",
                ContentStatus.PUBLISHED,
                requester,
                Instant.parse("2026-08-16T04:00:00Z")
            )
        ));
    }
}
