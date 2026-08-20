package io.regionevent.regioneventbackend.domain.content.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.entity.ContentWithdrawalRequestStatus;
import io.regionevent.regioneventbackend.domain.content.service.ContentWithdrawalReviewDetailResult;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@ExtendWith(OutputCaptureExtension.class)
@ContentControllerWebMvcTest
class ContentWithdrawalReviewDetailControllerWebMvcTest
    extends ContentControllerWebMvcTestSupport {

    private static final long WITHDRAWAL_REQUEST_ID = 7001L;
    private static final long CONTENT_ID = 101L;

    @Test
    void 요청_상세의_전체_필드와_시간_형식을_200_공통_응답으로_반환한다() throws Exception {
        when(getContentWithdrawalReviewDetailUseCase.get(
            AUTHENTICATED_USER_ID,
            WITHDRAWAL_REQUEST_ID
        )).thenReturn(result(new ContentWithdrawalReviewDetailResult.Requester(20L, "김운영")));

        mockMvc.perform(authenticated(get(
                "/api/v1/region-admin/content-withdrawal-requests/{withdrawalRequestId}",
                WITHDRAWAL_REQUEST_ID
            )))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message")
                .value("전체 콘텐츠 철회 요청 상세 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.withdrawalRequestId").value("7001"))
            .andExpect(jsonPath("$.data.status").value("PENDING"))
            .andExpect(jsonPath("$.data.content.contentId").value("101"))
            .andExpect(jsonPath("$.data.content.contentType").value("EVENT_EXPERIENCE"))
            .andExpect(jsonPath("$.data.content.title").value("김해 가야문화 체험"))
            .andExpect(jsonPath("$.data.content.status").value("PUBLISHED"))
            .andExpect(jsonPath("$.data.content.publishAt")
                .value("2026-08-01T09:00:00+09:00"))
            .andExpect(jsonPath("$.data.requester.userId").value("20"))
            .andExpect(jsonPath("$.data.requester.name").value("김운영"))
            .andExpect(jsonPath("$.data.requestReason").value("운영 계획 변경"))
            .andExpect(jsonPath("$.data.requestedAt").value("2026-08-16T04:00:00Z"))
            .andExpect(jsonPath("$.data.idempotencyKeyHash").doesNotExist())
            .andExpect(jsonPath("$.data.reviewedBy").doesNotExist())
            .andExpect(jsonPath("$.data.rejectionReason").doesNotExist())
            .andExpect(jsonPath("$.data.invalidatedBy").doesNotExist())
            .andExpect(jsonPath("$.data.invalidationReason").doesNotExist());

        verify(getContentWithdrawalReviewDetailUseCase).get(
            AUTHENTICATED_USER_ID,
            WITHDRAWAL_REQUEST_ID
        );
    }

    @Test
    void 요청자_연결이_없으면_requester를_null로_반환한다() throws Exception {
        when(getContentWithdrawalReviewDetailUseCase.get(
            AUTHENTICATED_USER_ID,
            WITHDRAWAL_REQUEST_ID
        )).thenReturn(result(null));

        mockMvc.perform(authenticated(get(
                "/api/v1/region-admin/content-withdrawal-requests/{withdrawalRequestId}",
                WITHDRAWAL_REQUEST_ID
            )))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.requester").value((Object) null));
    }

    @Test
    void 요청_ID_형식에_따라_입력과_타입_오류를_구분하고_원문을_로그에_남기지_않는다(
        CapturedOutput output
    ) throws Exception {
        expectPathError("0", "INVALID_INPUT");
        expectPathError("-1", "INVALID_INPUT");
        expectPathError("01", "INVALID_INPUT");
        expectPathError("not-a-number", "INVALID_TYPE");
        expectPathError("9223372036854775808", "INVALID_TYPE");

        verify(getContentWithdrawalReviewDetailUseCase, never()).get(any(), any());
        assertThat(output).contains(
            "Content withdrawal review detail queried.",
            "withdrawalRequestId=null, resultCode=INVALID_INPUT",
            "withdrawalRequestId=null, resultCode=INVALID_TYPE",
            "uri=/api/v1/region-admin/content-withdrawal-requests/{withdrawalRequestId}, status=400"
        ).doesNotContain(
            "uri=/api/v1/region-admin/content-withdrawal-requests/0",
            "uri=/api/v1/region-admin/content-withdrawal-requests/-1",
            "uri=/api/v1/region-admin/content-withdrawal-requests/01",
            "not-a-number",
            "9223372036854775808"
        );
    }

    @Test
    void 인증이_없으면_유스케이스를_호출하지_않고_원문_ID_없이_실패를_기록한다(
        CapturedOutput output
    ) throws Exception {
        mockMvc.perform(get(
                "/api/v1/region-admin/content-withdrawal-requests/{withdrawalRequestId}",
                WITHDRAWAL_REQUEST_ID
            ))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.statusCode").value(401))
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verify(getContentWithdrawalReviewDetailUseCase, never()).get(any(), any());
        assertThat(output).contains(
            "withdrawalRequestId=null, resultCode=UNAUTHENTICATED",
            "uri=/api/v1/region-admin/content-withdrawal-requests/{withdrawalRequestId}, status=401"
        ).doesNotContain("uri=/api/v1/region-admin/content-withdrawal-requests/7001");
    }

    @Test
    void 권한_대상_정합성_오류를_공통_오류_응답으로_반환한다() throws Exception {
        expectBusinessError(ErrorCode.FORBIDDEN, 403, "FORBIDDEN");
        expectBusinessError(ErrorCode.NOT_FOUND, 404, "NOT_FOUND");
        expectBusinessError(ErrorCode.INTERNAL_SERVER_ERROR, 500, "INTERNAL_SERVER_ERROR");
    }

    private void expectPathError(String withdrawalRequestId, String expectedCode) throws Exception {
        mockMvc.perform(authenticated(get(
                "/api/v1/region-admin/content-withdrawal-requests/{withdrawalRequestId}",
                withdrawalRequestId
            )))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.statusCode").value(400))
            .andExpect(jsonPath("$.code").value(expectedCode));
    }

    private void expectBusinessError(
        ErrorCode errorCode,
        int expectedStatus,
        String expectedCode
    ) throws Exception {
        doThrow(new BusinessException(errorCode))
            .when(getContentWithdrawalReviewDetailUseCase)
            .get(AUTHENTICATED_USER_ID, WITHDRAWAL_REQUEST_ID);

        mockMvc.perform(authenticated(get(
                "/api/v1/region-admin/content-withdrawal-requests/{withdrawalRequestId}",
                WITHDRAWAL_REQUEST_ID
            )))
            .andExpect(status().is(expectedStatus))
            .andExpect(jsonPath("$.statusCode").value(expectedStatus))
            .andExpect(jsonPath("$.code").value(expectedCode))
            .andExpect(jsonPath("$.data").value((Object) null));
    }

    private ContentWithdrawalReviewDetailResult result(
        ContentWithdrawalReviewDetailResult.Requester requester
    ) {
        return new ContentWithdrawalReviewDetailResult(
            WITHDRAWAL_REQUEST_ID,
            ContentWithdrawalRequestStatus.PENDING,
            new ContentWithdrawalReviewDetailResult.Content(
                CONTENT_ID,
                ContentType.EVENT_EXPERIENCE,
                "김해 가야문화 체험",
                ContentStatus.PUBLISHED,
                Instant.parse("2026-08-01T00:00:00Z")
            ),
            requester,
            "운영 계획 변경",
            Instant.parse("2026-08-16T04:00:00Z")
        );
    }
}
