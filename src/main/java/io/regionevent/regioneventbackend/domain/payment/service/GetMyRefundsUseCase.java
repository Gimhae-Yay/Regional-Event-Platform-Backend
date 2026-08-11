package io.regionevent.regioneventbackend.domain.payment.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.payment.entity.Refund;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.service.AppUserService;

@Service
public class GetMyRefundsUseCase {

    private final AppUserService appUserService;
    private final RefundService refundService;

    public GetMyRefundsUseCase(
        AppUserService appUserService,
        RefundService refundService
    ) {
        this.appUserService = appUserService;
        this.refundService = refundService;
    }

    @Transactional(readOnly = true)
    public List<Refund> findAll(Long userId) {
        AppUser user = appUserService.findActiveUser(userId);
        return refundService.findAllOwnedByUserId(user.getUserId());
    }
}
