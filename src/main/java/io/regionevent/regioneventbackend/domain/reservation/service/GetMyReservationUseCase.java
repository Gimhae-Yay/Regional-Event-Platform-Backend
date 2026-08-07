package io.regionevent.regioneventbackend.domain.reservation.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.service.AppUserService;

@Service
public class GetMyReservationUseCase {

    private final AppUserService appUserService;
    private final ReservationReadService reservationReadService;

    public GetMyReservationUseCase(
        AppUserService appUserService,
        ReservationReadService reservationReadService
    ) {
        this.appUserService = appUserService;
        this.reservationReadService = reservationReadService;
    }

    @Transactional(readOnly = true)
    public ReservationReadResult find(Long userId, Long reservationId) {
        AppUser user = appUserService.findActiveUser(userId);
        return reservationReadService.findOwnedByReservationId(user.getUserId(), reservationId);
    }
}
