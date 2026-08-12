package io.regionevent.regioneventbackend.domain.payment.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.payment.entity.PaymentDiscrepancy;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentDiscrepancyAction;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentVerification;
import io.regionevent.regioneventbackend.domain.user.service.PlatformAdminAuthorizationService;

@Service
public class GetPaymentDiscrepancyUseCase {

    private final PlatformAdminAuthorizationService platformAdminAuthorizationService;
    private final PaymentDiscrepancyService paymentDiscrepancyService;
    private final PaymentVerificationService paymentVerificationService;
    private final PaymentDiscrepancyActionService paymentDiscrepancyActionService;

    public GetPaymentDiscrepancyUseCase(
        PlatformAdminAuthorizationService platformAdminAuthorizationService,
        PaymentDiscrepancyService paymentDiscrepancyService,
        PaymentVerificationService paymentVerificationService,
        PaymentDiscrepancyActionService paymentDiscrepancyActionService
    ) {
        this.platformAdminAuthorizationService = platformAdminAuthorizationService;
        this.paymentDiscrepancyService = paymentDiscrepancyService;
        this.paymentVerificationService = paymentVerificationService;
        this.paymentDiscrepancyActionService = paymentDiscrepancyActionService;
    }

    @Transactional(readOnly = true)
    public PaymentDiscrepancyDetailInfo get(Long actorUserId, Long discrepancyId) {
        platformAdminAuthorizationService.requireAuthorizedPlatformAdmin(actorUserId);
        PaymentDiscrepancy discrepancy = paymentDiscrepancyService.findById(discrepancyId);
        List<PaymentVerification> verifications = paymentVerificationService.findAllByPaymentId(
            discrepancy.getPayment().getPaymentId()
        );
        List<PaymentDiscrepancyAction> actions = paymentDiscrepancyActionService.findAllByDiscrepancyId(
            discrepancy.getPaymentDiscrepancyId()
        );
        return PaymentDiscrepancyDetailInfo.from(discrepancy, verifications, actions);
    }
}
