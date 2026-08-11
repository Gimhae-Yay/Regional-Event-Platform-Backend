package io.regionevent.regioneventbackend.domain.payment.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.payment.entity.Refund;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.service.AppUserService;

@Service
public class GetMyRefundUseCase {

    private final AppUserService appUserService;
    private final RefundService refundService;

    public GetMyRefundUseCase(
        AppUserService appUserService,
        RefundService refundService
    ) {
        this.appUserService = appUserService;
        this.refundService = refundService;
    }

    @Transactional(readOnly = true)
    public Refund find(Long userId, Long refundId) {
        AppUser user = appUserService.findActiveUser(userId);
        return refundService.findOwnedByRefundId(user.getUserId(), refundId);
    }
}
