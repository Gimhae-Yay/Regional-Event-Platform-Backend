package io.regionevent.regioneventbackend.domain.payment.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.regionevent.regioneventbackend.domain.payment.entity.RefundAttemptInitiatorKind;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundAttemptOutcomeKind;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundFailureReasonCode;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundStatus;
import io.regionevent.regioneventbackend.domain.payment.service.GetRefundFailureUseCase;
import io.regionevent.regioneventbackend.domain.payment.service.RefundFailureDetailInfo;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.config.SecurityConfig;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.error.GlobalExceptionHandler;
import io.regionevent.regioneventbackend.global.security.access.AccessTokenTestFactory;
import io.regionevent.regioneventbackend.global.security.access.JwtAccessTokenService;

@WebMvcTest(RefundFailureDetailController.class)
@Import({SecurityConfig.class, RequestIdFilter.class, GlobalExceptionHandler.class})
class RefundFailureDetailControllerWebMvcTest {

    private static final long USER_ID = 100L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAccessTokenService jwtAccessTokenService;

    @MockitoBean
    private GetRefundFailureUseCase getRefundFailureUseCase;

    @Test
    void get_환불과결제및오름차순시도이력을반환하고비밀값을노출하지않는다() throws Exception {
        when(getRefundFailureUseCase.get(USER_ID, 552L)).thenReturn(detail());

        mockMvc.perform(authenticated(get("/api/v1/platform-admin/refund-failures/552")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusCode").value(200))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.refund.refundId").value("552"))
            .andExpect(jsonPath("$.data.refund.completedAt").isEmpty())
            .andExpect(jsonPath("$.data.payment.orderId").value("ORD-20260807-3K9P1M"))
            .andExpect(jsonPath("$.data.payment.portonePaymentId").isEmpty())
            .andExpect(jsonPath("$.data.attempts[0].attemptNo").value(1))
            .andExpect(jsonPath("$.data.attempts[0].failureReasonCode").value("TIMEOUT"))
            .andExpect(jsonPath("$.data.attempts[0].externalStatus").isEmpty())
            .andExpect(jsonPath("$.data.attempts[1].attemptNo").value(2))
            .andExpect(jsonPath("$.data.attempts[1].portoneCancellationId").value("cancel-2"))
            .andExpect(jsonPath("$.data.attempts[1].externalStatus").value("SUCCEEDED"))
            .andExpect(jsonPath("$.data.attempts[0].resultHash").doesNotExist())
            .andExpect(jsonPath("$.data.rawPayload").doesNotExist());

        verify(getRefundFailureUseCase).get(USER_ID, 552L);
    }

    @Test
    void get_양의정수가아닌환불식별자는조회하지않고입력유형오류를반환한다() throws Exception {
        mockMvc.perform(authenticated(get("/api/v1/platform-admin/refund-failures/0")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_TYPE"));

        verifyNoInteractions(getRefundFailureUseCase);
    }

    @Test
    void get_대상이없으면존재하지않음오류를반환한다() throws Exception {
        when(getRefundFailureUseCase.get(USER_ID, 552L)).thenThrow(
            new BusinessException(ErrorCode.NOT_FOUND)
        );

        mockMvc.perform(authenticated(get("/api/v1/platform-admin/refund-failures/552")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        verify(getRefundFailureUseCase).get(USER_ID, 552L);
    }

    @Test
    void get_미인증이면조회하지않고미인증오류를반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/platform-admin/refund-failures/552"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verifyNoInteractions(getRefundFailureUseCase);
    }

    private RefundFailureDetailInfo detail() {
        return new RefundFailureDetailInfo(
            new RefundFailureDetailInfo.RefundInfo(
                552L,
                903L,
                124L,
                12_000L,
                "KRW",
                RefundStatus.DISCREPANT,
                Instant.parse("2026-08-07T01:10:00Z"),
                null
            ),
            new RefundFailureDetailInfo.PaymentInfo(
                903L,
                "ORD-20260807-3K9P1M",
                null,
                12_000L,
                "KRW"
            ),
            List.of(
                new RefundFailureDetailInfo.AttemptInfo(
                    701L,
                    1,
                    RefundAttemptInitiatorKind.SYSTEM,
                    null,
                    RefundAttemptOutcomeKind.NO_RESPONSE,
                    RefundFailureReasonCode.TIMEOUT,
                    null,
                    Instant.parse("2026-08-07T01:10:31Z")
                ),
                new RefundFailureDetailInfo.AttemptInfo(
                    702L,
                    2,
                    RefundAttemptInitiatorKind.PLATFORM_ADMIN,
                    "cancel-2",
                    RefundAttemptOutcomeKind.RESPONDED,
                    null,
                    "SUCCEEDED",
                    Instant.parse("2026-08-07T01:11:31Z")
                )
            )
        );
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authenticated(
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request
    ) {
        return request.header(AUTHORIZATION, "Bearer " + AccessTokenTestFactory.issueForAuthenticatedRequest(jwtAccessTokenService, USER_ID));
    }
}
