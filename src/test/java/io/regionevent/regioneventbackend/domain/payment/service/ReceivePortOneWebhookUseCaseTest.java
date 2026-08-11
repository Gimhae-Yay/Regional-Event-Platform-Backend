package io.regionevent.regioneventbackend.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.ObjectMapper;

import io.regionevent.regioneventbackend.domain.coupon.service.CouponRedemptionService;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponService;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponStatusHistoryService;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentWebhook;
import io.regionevent.regioneventbackend.domain.payment.port.out.PortOneLookupException;
import io.regionevent.regioneventbackend.domain.payment.port.out.PortOnePaymentGateway;
import io.regionevent.regioneventbackend.domain.reservation.service.CapacityHoldService;
import io.regionevent.regioneventbackend.domain.reservation.service.ReservationPriceSnapshotService;
import io.regionevent.regioneventbackend.domain.reservation.service.ReservationService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.infra.payment.PortOneWebhookSignatureVerifier;

class ReceivePortOneWebhookUseCaseTest {

    private static final String WEBHOOK_ID = "webhook-id";
    private static final String WEBHOOK_TIMESTAMP = "1785983465";
    private static final String WEBHOOK_SIGNATURE = "v1,signature";
    private static final String PAYMENT_ID = "order-1";
    private static final String TRANSACTION_ID = "transaction-1";

    private final PortOneWebhookSignatureVerifier signatureVerifier = mock(
        PortOneWebhookSignatureVerifier.class
    );
    private final PortOnePaymentGateway paymentGateway = mock(PortOnePaymentGateway.class);
    private final PaymentService paymentService = mock(PaymentService.class);
    private final PaymentWebhookService paymentWebhookService = mock(PaymentWebhookService.class);
    private final TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);

    private ReceivePortOneWebhookUseCase useCase;

    @BeforeEach
    void setUp() {
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
        useCase = new ReceivePortOneWebhookUseCase(
            new ObjectMapper(),
            signatureVerifier,
            paymentGateway,
            paymentService,
            paymentWebhookService,
            mock(PaymentVerificationService.class),
            mock(PaymentDiscrepancyService.class),
            mock(CapacityHoldService.class),
            mock(ReservationPriceSnapshotService.class),
            mock(ReservationService.class),
            mock(CouponService.class),
            mock(CouponStatusHistoryService.class),
            mock(CouponRedemptionService.class),
            transactionTemplate
        );
    }

    @Test
    void receive_signatureVerificationFails_doesNotLookupOrPersistWebhook() {
        doThrow(new PortOneWebhookSignatureVerifier.InvalidWebhookSignatureException())
            .when(signatureVerifier)
            .verify(anyString(), anyString(), anyString(), anyString());

        assertThatThrownBy(() -> useCase.receive(
            WEBHOOK_ID,
            WEBHOOK_TIMESTAMP,
            WEBHOOK_SIGNATURE,
            validPaymentEvent()
        )).isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(ErrorCode.WEBHOOK_SIGNATURE_INVALID);

        verify(paymentGateway, never()).findByPaymentId(anyString());
        verify(paymentWebhookService, never()).create(any());
    }

    @Test
    void receive_paymentNotFound_persistsOnlyNormalizedHashWithoutRawBody() {
        String rawBody = validPaymentEvent().replace(
            "\"data\": {",
            "\"secret\": \"secret-value\",\n  \"data\": {"
        );
        when(paymentGateway.findByPaymentId(PAYMENT_ID)).thenReturn(new PortOnePaymentGateway.PortOnePayment(
            PAYMENT_ID,
            TRANSACTION_ID,
            20_000,
            "KRW",
            "PAID"
        ));
        when(paymentService.findByOrderIdForUpdate(PAYMENT_ID)).thenReturn(Optional.empty());
        when(paymentWebhookService.existsByProviderEventId(WEBHOOK_ID)).thenReturn(false);
        ArgumentCaptor<PaymentWebhook> webhookCaptor = ArgumentCaptor.forClass(PaymentWebhook.class);

        useCase.receive(WEBHOOK_ID, WEBHOOK_TIMESTAMP, WEBHOOK_SIGNATURE, rawBody);

        verify(paymentWebhookService).create(webhookCaptor.capture());
        PaymentWebhook webhook = webhookCaptor.getValue();
        assertThat(webhook.getProviderEventId()).isEqualTo(WEBHOOK_ID);
        assertThat(webhook.getPayloadHash()).doesNotContain("secret-value");
        assertThat(webhook.getPayloadHash()).doesNotContain(rawBody);
        verify(paymentGateway).findByPaymentId(PAYMENT_ID);
    }

    @Test
    void receive_paymentLookupFails_doesNotPersistWebhookOrVerification() {
        when(paymentGateway.findByPaymentId(PAYMENT_ID)).thenThrow(new PortOneLookupException(
            new IllegalStateException("provider timeout")
        ));

        assertThatThrownBy(() -> useCase.receive(
            WEBHOOK_ID,
            WEBHOOK_TIMESTAMP,
            WEBHOOK_SIGNATURE,
            validPaymentEvent()
        )).isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR);

        verify(paymentWebhookService, never()).create(any());
        verify(paymentService, never()).findByOrderIdForUpdate(anyString());
    }

    private String validPaymentEvent() {
        return """
            {
              "type": "Transaction.Paid",
              "timestamp": "2026-08-06T02:31:05Z",
              "data": {
                "storeId": "store-1",
                "paymentId": "%s",
                "transactionId": "%s"
              }
            }
            """.formatted(PAYMENT_ID, TRANSACTION_ID);
    }
}
