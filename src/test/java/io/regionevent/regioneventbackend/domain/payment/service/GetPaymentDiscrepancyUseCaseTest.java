package io.regionevent.regioneventbackend.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.payment.entity.Payment;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentDiscrepancy;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentDiscrepancyAction;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentStatus;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentVerification;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHold;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationPriceSnapshot;
import io.regionevent.regioneventbackend.domain.user.service.PlatformAdminAuthorizationService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class GetPaymentDiscrepancyUseCaseTest {

    private static final Long ACTOR_USER_ID = 101L;
    private static final Long DISCREPANCY_ID = 301L;
    private static final Long PAYMENT_ID = 902L;

    @Test
    void get_활성전체관리자_오름차순검증및조치이력을읽기전용으로반환한다() {
        PlatformAdminAuthorizationService authorizationService = mock(
            PlatformAdminAuthorizationService.class
        );
        PaymentDiscrepancyService paymentDiscrepancyService = mock(PaymentDiscrepancyService.class);
        PaymentVerificationService paymentVerificationService = mock(PaymentVerificationService.class);
        PaymentDiscrepancyActionService paymentDiscrepancyActionService = mock(
            PaymentDiscrepancyActionService.class
        );
        PaymentDiscrepancy discrepancy = discrepancy();
        List<PaymentVerification> verifications = List.of(
            verification(551L, "CONFIRM_REQUEST", "DISCREPANT", Instant.parse("2026-08-06T03:05:00Z")),
            verification(552L, "WEBHOOK", "PENDING", Instant.parse("2026-08-06T03:06:00Z"))
        );
        List<PaymentDiscrepancyAction> actions = List.of(
            action(701L, "NO_ISSUE_CLOSE", Instant.parse("2026-08-06T03:07:00Z")),
            action(702L, "FULL_REFUND_REQUEST", Instant.parse("2026-08-06T03:08:00Z"))
        );
        when(paymentDiscrepancyService.findById(DISCREPANCY_ID)).thenReturn(discrepancy);
        when(paymentVerificationService.findAllByPaymentId(PAYMENT_ID)).thenReturn(verifications);
        when(paymentDiscrepancyActionService.findAllByDiscrepancyId(DISCREPANCY_ID)).thenReturn(actions);
        GetPaymentDiscrepancyUseCase useCase = new GetPaymentDiscrepancyUseCase(
            authorizationService,
            paymentDiscrepancyService,
            paymentVerificationService,
            paymentDiscrepancyActionService
        );

        PaymentDiscrepancyDetailInfo actual = useCase.get(ACTOR_USER_ID, DISCREPANCY_ID);

        assertThat(actual.discrepancyId()).isEqualTo(DISCREPANCY_ID);
        assertThat(actual.paymentStatus()).isEqualTo("DISCREPANT");
        assertThat(actual.verifications())
            .extracting(PaymentDiscrepancyDetailInfo.VerificationInfo::paymentVerificationId)
            .containsExactly(551L, 552L);
        assertThat(actual.verifications())
            .extracting(PaymentDiscrepancyDetailInfo.VerificationInfo::matched)
            .containsExactly(false, true);
        assertThat(actual.actions())
            .extracting(PaymentDiscrepancyDetailInfo.ActionInfo::actionId)
            .containsExactly(701L, 702L);
        verify(authorizationService).requireAuthorizedPlatformAdmin(ACTOR_USER_ID);
        verify(paymentDiscrepancyService).findById(DISCREPANCY_ID);
        verify(paymentVerificationService).findAllByPaymentId(PAYMENT_ID);
        verify(paymentDiscrepancyActionService).findAllByDiscrepancyId(DISCREPANCY_ID);
        verifyNoMoreInteractions(
            authorizationService,
            paymentDiscrepancyService,
            paymentVerificationService,
            paymentDiscrepancyActionService
        );
    }

    @Test
    void get_권한없는사용자_상세와이력을조회하지않는다() {
        PlatformAdminAuthorizationService authorizationService = mock(
            PlatformAdminAuthorizationService.class
        );
        PaymentDiscrepancyService paymentDiscrepancyService = mock(PaymentDiscrepancyService.class);
        PaymentVerificationService paymentVerificationService = mock(PaymentVerificationService.class);
        PaymentDiscrepancyActionService paymentDiscrepancyActionService = mock(
            PaymentDiscrepancyActionService.class
        );
        when(authorizationService.requireAuthorizedPlatformAdmin(ACTOR_USER_ID)).thenThrow(
            new BusinessException(ErrorCode.FORBIDDEN)
        );
        GetPaymentDiscrepancyUseCase useCase = new GetPaymentDiscrepancyUseCase(
            authorizationService,
            paymentDiscrepancyService,
            paymentVerificationService,
            paymentDiscrepancyActionService
        );

        assertThatThrownBy(() -> useCase.get(ACTOR_USER_ID, DISCREPANCY_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN)
            );

        verify(authorizationService).requireAuthorizedPlatformAdmin(ACTOR_USER_ID);
        verifyNoInteractions(
            paymentDiscrepancyService,
            paymentVerificationService,
            paymentDiscrepancyActionService
        );
    }

    @Test
    void get_없는불일치_검증과조치이력을조회하지않는다() {
        PlatformAdminAuthorizationService authorizationService = mock(
            PlatformAdminAuthorizationService.class
        );
        PaymentDiscrepancyService paymentDiscrepancyService = mock(PaymentDiscrepancyService.class);
        PaymentVerificationService paymentVerificationService = mock(PaymentVerificationService.class);
        PaymentDiscrepancyActionService paymentDiscrepancyActionService = mock(
            PaymentDiscrepancyActionService.class
        );
        when(paymentDiscrepancyService.findById(DISCREPANCY_ID)).thenThrow(
            new BusinessException(ErrorCode.NOT_FOUND)
        );
        GetPaymentDiscrepancyUseCase useCase = new GetPaymentDiscrepancyUseCase(
            authorizationService,
            paymentDiscrepancyService,
            paymentVerificationService,
            paymentDiscrepancyActionService
        );

        assertThatThrownBy(() -> useCase.get(ACTOR_USER_ID, DISCREPANCY_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND)
            );

        verify(authorizationService).requireAuthorizedPlatformAdmin(ACTOR_USER_ID);
        verify(paymentDiscrepancyService).findById(DISCREPANCY_ID);
        verifyNoInteractions(paymentVerificationService, paymentDiscrepancyActionService);
    }

    private PaymentDiscrepancy discrepancy() {
        CapacityHold capacityHold = mock(CapacityHold.class);
        ReservationPriceSnapshot snapshot = mock(ReservationPriceSnapshot.class);
        Payment payment = mock(Payment.class);
        PaymentDiscrepancy discrepancy = mock(PaymentDiscrepancy.class);
        when(capacityHold.getHoldId()).thenReturn(790L);
        when(snapshot.getFinalAmount()).thenReturn(15_000L);
        when(snapshot.getCurrency()).thenReturn("KRW");
        when(payment.getPaymentId()).thenReturn(PAYMENT_ID);
        when(payment.getCapacityHold()).thenReturn(capacityHold);
        when(payment.getReservationPriceSnapshot()).thenReturn(snapshot);
        when(payment.getOrderId()).thenReturn("ORD-20260806-7H2P4X");
        when(payment.getPortonePaymentId()).thenReturn("portone-txn-abc123");
        when(payment.getStatus()).thenReturn(PaymentStatus.DISCREPANT);
        when(discrepancy.getPaymentDiscrepancyId()).thenReturn(DISCREPANCY_ID);
        when(discrepancy.getPayment()).thenReturn(payment);
        when(discrepancy.getDiscrepancyType()).thenReturn("AMOUNT_MISMATCH");
        when(discrepancy.getStatus()).thenReturn("OPEN");
        when(discrepancy.getDetectedAt()).thenReturn(Instant.parse("2026-08-06T03:05:00Z"));
        return discrepancy;
    }

    private PaymentVerification verification(
        Long verificationId,
        String reason,
        String internalDecision,
        Instant verifiedAt
    ) {
        PaymentVerification verification = mock(PaymentVerification.class);
        when(verification.getPaymentVerificationId()).thenReturn(verificationId);
        when(verification.getVerificationReason()).thenReturn(reason);
        when(verification.getExternalStatus()).thenReturn("PAID");
        when(verification.getObservedAmount()).thenReturn(14_000L);
        when(verification.getInternalDecision()).thenReturn(internalDecision);
        when(verification.getVerifiedAt()).thenReturn(verifiedAt);
        return verification;
    }

    private PaymentDiscrepancyAction action(
        Long actionId,
        String actionType,
        Instant actedAt
    ) {
        PaymentDiscrepancyAction action = mock(PaymentDiscrepancyAction.class);
        when(action.getPaymentDiscrepancyActionId()).thenReturn(actionId);
        when(action.getActionType()).thenReturn(actionType);
        when(action.getEvidenceReference()).thenReturn("evidence-reference");
        when(action.getReasonCode()).thenReturn("MANUAL_REVIEW");
        when(action.getActedAt()).thenReturn(actedAt);
        return action;
    }
}
