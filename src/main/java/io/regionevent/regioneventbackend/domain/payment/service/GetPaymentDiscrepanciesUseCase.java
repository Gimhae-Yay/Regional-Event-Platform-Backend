package io.regionevent.regioneventbackend.domain.payment.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.user.service.PlatformAdminAuthorizationService;

@Service
public class GetPaymentDiscrepanciesUseCase {

    private final PlatformAdminAuthorizationService platformAdminAuthorizationService;
    private final PaymentDiscrepancyService paymentDiscrepancyService;

    public GetPaymentDiscrepanciesUseCase(
        PlatformAdminAuthorizationService platformAdminAuthorizationService,
        PaymentDiscrepancyService paymentDiscrepancyService
    ) {
        this.platformAdminAuthorizationService = platformAdminAuthorizationService;
        this.paymentDiscrepancyService = paymentDiscrepancyService;
    }

    @Transactional(readOnly = true)
    public List<PaymentDiscrepancyListInfo> get(
        Long actorUserId,
        String status
    ) {
        platformAdminAuthorizationService.requireAuthorizedPlatformAdmin(actorUserId);
        return paymentDiscrepancyService.findAllByStatus(status).stream()
            .map(PaymentDiscrepancyListInfo::from)
            .toList();
    }
}
