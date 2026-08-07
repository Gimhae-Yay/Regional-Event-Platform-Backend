package io.regionevent.regioneventbackend.domain.reservation.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.service.AppUserService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class GetMyReservationQrUseCase {

    private final AppUserService appUserService;
    private final ReservationService reservationService;

    public GetMyReservationQrUseCase(
        AppUserService appUserService,
        ReservationService reservationService
    ) {
        this.appUserService = appUserService;
        this.reservationService = reservationService;
    }

    @Transactional
    public MyReservationQrResult get(Long userId, Long reservationId) {
        AppUser user = appUserService.findActiveUserForUpdate(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
        return reservationService.issueQr(reservationId, user);
    }
}
