package io.regionevent.regioneventbackend.domain.payment.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.payment.entity.Payment;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.service.AppUserService;

@Service
public class GetMyPaymentUseCase {

    private final AppUserService appUserService;
    private final PaymentReadService paymentReadService;

    public GetMyPaymentUseCase(
        AppUserService appUserService,
        PaymentReadService paymentReadService
    ) {
        this.appUserService = appUserService;
        this.paymentReadService = paymentReadService;
    }

    @Transactional(readOnly = true)
    public Payment find(Long userId, Long paymentId) {
        AppUser user = appUserService.findActiveUser(userId);
        return paymentReadService.findOwnedByPaymentId(user.getUserId(), paymentId);
    }
}
