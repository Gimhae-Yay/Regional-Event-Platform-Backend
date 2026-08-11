package io.regionevent.regioneventbackend.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.ObjectMapper;

import io.regionevent.regioneventbackend.domain.coupon.service.CouponRedemptionService;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponService;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponStatusHistoryService;
import io.regionevent.regioneventbackend.domain.content.service.ContentService;
import io.regionevent.regioneventbackend.domain.content.service.ContentSessionService;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.payment.entity.Payment;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentDiscrepancy;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentStatus;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentWebhook;
import io.regionevent.regioneventbackend.domain.payment.port.out.PortOneLookupException;
import io.regionevent.regioneventbackend.domain.payment.port.out.PortOnePaymentGateway;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHold;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationPriceSnapshot;
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
    private final PaymentVerificationService paymentVerificationService = mock(PaymentVerificationService.class);
    private final PaymentDiscrepancyService paymentDiscrepancyService = mock(PaymentDiscrepancyService.class);
    private final ContentService contentService = mock(ContentService.class);
    private final ContentSessionService contentSessionService = mock(ContentSessionService.class);
    private final CapacityHoldService capacityHoldService = mock(CapacityHoldService.class);
    private final ReservationPriceSnapshotService reservationPriceSnapshotService = mock(
        ReservationPriceSnapshotService.class
    );
    private final ReservationService reservationService = mock(ReservationService.class);
    private final RecordAuditEventUseCase recordAuditEventUseCase = mock(RecordAuditEventUseCase.class);
    private final TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
    private final EntityManager entityManager = mock(EntityManager.class);

    private ReceivePortOneWebhookUseCase useCase;

    @BeforeEach
    void setUp() {
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
        doAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        }).when(transactionTemplate).execute(any());
        doAnswer(invocation -> {
            Payment payment = invocation.getArgument(0, PaymentDiscrepancy.class).getPayment();
            PaymentDiscrepancy discrepancy = mock(PaymentDiscrepancy.class);
            when(discrepancy.getPaymentDiscrepancyId()).thenReturn(1L);
            when(discrepancy.getPayment()).thenReturn(payment);
            return discrepancy;
        }).when(paymentDiscrepancyService).create(any(PaymentDiscrepancy.class));
        useCase = new ReceivePortOneWebhookUseCase(
            new ObjectMapper(),
            signatureVerifier,
            paymentGateway,
            paymentService,
            paymentWebhookService,
            paymentVerificationService,
            paymentDiscrepancyService,
            contentService,
            contentSessionService,
            capacityHoldService,
            reservationPriceSnapshotService,
            reservationService,
            mock(CouponService.class),
            mock(CouponStatusHistoryService.class),
            mock(CouponRedemptionService.class),
            recordAuditEventUseCase,
            transactionTemplate,
            entityManager
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
        verify(paymentWebhookService, never()).createIfAbsent(any());
    }

    @Test
    void receive_invalidBodyTimestamp_doesNotLookupOrPersistWebhook() {
        String rawBody = validPaymentEvent().replace(
            "\"timestamp\": \"2026-08-06T02:31:05Z\"",
            "\"timestamp\": \"invalid\""
        );

        assertThatThrownBy(() -> useCase.receive(
            WEBHOOK_ID,
            WEBHOOK_TIMESTAMP,
            WEBHOOK_SIGNATURE,
            rawBody
        )).isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(ErrorCode.INVALID_JSON);

        verify(paymentGateway, never()).findByPaymentId(anyString());
        verify(paymentWebhookService, never()).createIfAbsent(any());
    }

    @Test
    void receive_paymentNotFound_persistsOnlyNormalizedHashWithoutRawBody() {
        String rawBody = validPaymentEvent().replace(
            "\"data\": {",
            "\"secret\": \"secret-value\",\n  \"data\": {"
        );
        when(paymentService.findByOrderId(PAYMENT_ID)).thenReturn(Optional.empty());
        when(paymentWebhookService.existsByProviderEventId(WEBHOOK_ID)).thenReturn(false);
        ArgumentCaptor<PaymentWebhook> webhookCaptor = ArgumentCaptor.forClass(PaymentWebhook.class);

        useCase.receive(WEBHOOK_ID, WEBHOOK_TIMESTAMP, WEBHOOK_SIGNATURE, rawBody);

        verify(paymentWebhookService).createIfAbsent(webhookCaptor.capture());
        PaymentWebhook webhook = webhookCaptor.getValue();
        assertThat(webhook.getProviderEventId()).isEqualTo(WEBHOOK_ID);
        assertThat(webhook.getPayloadHash()).doesNotContain("secret-value");
        assertThat(webhook.getPayloadHash()).doesNotContain(rawBody);
        verify(paymentGateway, never()).findByPaymentId(anyString());
    }

    @Test
    void receive_paymentLookupFails_doesNotPersistWebhookOrVerification() {
        Payment payment = pendingPayment();
        when(paymentService.findByOrderId(PAYMENT_ID)).thenReturn(Optional.of(payment));
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

        verify(paymentWebhookService, never()).createIfAbsent(any());
        verify(paymentService, never()).findWebhookTargetByOrderIdForUpdate(anyString());
    }

    @Test
    void receive_existingWebhook_doesNotLookupPortOne() {
        when(paymentWebhookService.existsByProviderEventId(WEBHOOK_ID)).thenReturn(true);

        useCase.receive(WEBHOOK_ID, WEBHOOK_TIMESTAMP, WEBHOOK_SIGNATURE, validPaymentEvent());

        verify(paymentGateway, never()).findByPaymentId(anyString());
    }

    @Test
    void receive_finalizedPayment_doesNotLookupPortOne() {
        Payment payment = pendingPayment();
        when(payment.getStatus()).thenReturn(PaymentStatus.APPROVED);
        when(paymentWebhookService.existsByProviderEventId(WEBHOOK_ID)).thenReturn(false);

        useCase.receive(WEBHOOK_ID, WEBHOOK_TIMESTAMP, WEBHOOK_SIGNATURE, validPaymentEvent());

        verify(paymentGateway, never()).findByPaymentId(anyString());
        verify(paymentWebhookService).createIfAbsent(any(PaymentWebhook.class));
    }

    @Test
    void receive_explicitDecline_transitionsOnlyPaymentAndRecordsVerification() {
        Payment payment = pendingPayment();
        when(paymentGateway.findByPaymentId(PAYMENT_ID)).thenReturn(new PortOnePaymentGateway.PortOnePayment(
            PAYMENT_ID,
            TRANSACTION_ID,
            "store-1",
            20_000,
            "KRW",
            "DECLINED"
        ));
        when(paymentService.findByOrderIdForUpdate(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(paymentWebhookService.existsByProviderEventId(WEBHOOK_ID)).thenReturn(false);

        useCase.receive(WEBHOOK_ID, WEBHOOK_TIMESTAMP, WEBHOOK_SIGNATURE, validPaymentEvent());

        verify(payment).decline(any());
        verify(paymentVerificationService).create(any());
        verify(capacityHoldService, never()).consumeForPaidPaymentIfConfirmable(any(), any(), any());
        verify(reservationService, never()).createConfirmed(any());
    }

    @Test
    void receive_amountMismatch_marksPaymentDiscrepantAndRecordsDiscrepancy() {
        Payment payment = pendingPayment();
        when(paymentGateway.findByPaymentId(PAYMENT_ID)).thenReturn(new PortOnePaymentGateway.PortOnePayment(
            PAYMENT_ID,
            TRANSACTION_ID,
            "store-1",
            20_001,
            "KRW",
            "PAID"
        ));
        when(paymentService.findByOrderIdForUpdate(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(paymentWebhookService.existsByProviderEventId(WEBHOOK_ID)).thenReturn(false);

        useCase.receive(WEBHOOK_ID, WEBHOOK_TIMESTAMP, WEBHOOK_SIGNATURE, validPaymentEvent());

        verify(payment).markDiscrepant(eq(TRANSACTION_ID), any());
        verify(paymentDiscrepancyService).create(any());
        verify(capacityHoldService, never()).consumeForPaidPaymentIfConfirmable(any(), any(), any());
    }

    @Test
    void receive_reorderedWebhookAfterDecline_preservesFinalPaymentState() {
        Payment payment = pendingPayment();
        AtomicReference<PaymentStatus> status = new AtomicReference<>(PaymentStatus.PENDING);
        when(payment.getStatus()).thenAnswer(invocation -> status.get());
        doAnswer(invocation -> {
            status.set(PaymentStatus.DECLINED);
            return null;
        }).when(payment).decline(any());
        when(paymentGateway.findByPaymentId(PAYMENT_ID))
            .thenReturn(new PortOnePaymentGateway.PortOnePayment(
                PAYMENT_ID,
                TRANSACTION_ID,
                "store-1",
                20_000,
                "KRW",
                "DECLINED"
            ))
            .thenReturn(paidPayment());
        when(paymentService.findByOrderIdForUpdate(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(paymentWebhookService.existsByProviderEventId(anyString())).thenReturn(false);

        useCase.receive("webhook-declined", WEBHOOK_TIMESTAMP, WEBHOOK_SIGNATURE, validPaymentEvent());
        useCase.receive("webhook-paid", WEBHOOK_TIMESTAMP, WEBHOOK_SIGNATURE, validPaymentEvent());

        verify(payment).decline(any());
        verify(paymentDiscrepancyService, never()).create(any());
        verify(capacityHoldService, never()).consumeForPaidPaymentIfConfirmable(any(), any(), any());
    }

    private Payment pendingPayment() {
        CapacityHold capacityHold = mock(CapacityHold.class);
        Content content = mock(Content.class);
        ContentSession contentSession = mock(ContentSession.class);
        ReservationPriceSnapshot snapshot = mock(ReservationPriceSnapshot.class);
        Payment payment = mock(Payment.class);
        when(content.getContentId()).thenReturn(1L);
        when(contentSession.getSessionId()).thenReturn(1L);
        when(contentSession.getContent()).thenReturn(content);
        when(capacityHold.getHoldId()).thenReturn(1L);
        when(capacityHold.getContentSession()).thenReturn(contentSession);
        when(payment.getCapacityHold()).thenReturn(capacityHold);
        when(payment.getPaymentId()).thenReturn(1L);
        when(payment.getOrderId()).thenReturn(PAYMENT_ID);
        when(payment.getStatus()).thenReturn(PaymentStatus.PENDING);
        when(paymentService.findByOrderId(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(paymentService.findWebhookTargetByOrderIdForUpdate(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(contentService.findForUpdate(1L)).thenReturn(content);
        when(contentSessionService.findForUpdate(1L)).thenReturn(contentSession);
        when(capacityHoldService.findByHoldIdForUpdate(1L)).thenReturn(capacityHold);
        when(reservationPriceSnapshotService.findByHoldIdForUpdate(1L)).thenReturn(Optional.of(snapshot));
        when(snapshot.getCapacityHold()).thenReturn(capacityHold);
        when(snapshot.getFinalAmount()).thenReturn(20_000L);
        when(snapshot.getCurrency()).thenReturn("KRW");
        return payment;
    }

    private PortOnePaymentGateway.PortOnePayment paidPayment() {
        return new PortOnePaymentGateway.PortOnePayment(
            PAYMENT_ID,
            TRANSACTION_ID,
            "store-1",
            20_000,
            "KRW",
            "PAID"
        );
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
