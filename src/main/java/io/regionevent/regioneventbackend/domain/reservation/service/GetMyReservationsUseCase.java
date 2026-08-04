package io.regionevent.regioneventbackend.domain.reservation.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.service.AppUserService;

@Service
public class GetMyReservationsUseCase {

    private final AppUserService appUserService;
    private final ReservationReadService reservationReadService;

    public GetMyReservationsUseCase(
        AppUserService appUserService,
        ReservationReadService reservationReadService
    ) {
        this.appUserService = appUserService;
        this.reservationReadService = reservationReadService;
    }

    @Transactional(readOnly = true)
    public List<ReservationReadResult> findAll(Long userId) {
        AppUser user = appUserService.findActiveUser(userId);
        return reservationReadService.findAllOwnedByUserId(user.getUserId());
    }
}
