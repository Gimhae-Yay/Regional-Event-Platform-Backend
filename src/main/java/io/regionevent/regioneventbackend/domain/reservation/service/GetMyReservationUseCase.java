package io.regionevent.regioneventbackend.domain.reservation.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.review.service.ReviewService;
import io.regionevent.regioneventbackend.domain.review.service.ReviewService.ReviewReadResult;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.service.AppUserService;

@Service
public class GetMyReservationUseCase {

    private final AppUserService appUserService;
    private final ReservationReadService reservationReadService;
    private final ReviewService reviewService;

    public GetMyReservationUseCase(
        AppUserService appUserService,
        ReservationReadService reservationReadService,
        ReviewService reviewService
    ) {
        this.appUserService = appUserService;
        this.reservationReadService = reservationReadService;
        this.reviewService = reviewService;
    }

    @Transactional(readOnly = true)
    public MyReservationDetailResult find(Long userId, Long reservationId) {
        AppUser user = appUserService.findActiveUser(userId);
        ReservationReadResult reservation = reservationReadService.findOwnedByReservationId(
            user.getUserId(),
            reservationId
        );
        Long visitId = reservation.checkIn().visitId();
        List<Long> visitIds = visitId == null ? List.of() : List.of(visitId);
        Map<Long, ReviewReadResult> reviewsByVisitId = reviewService.findAllByVisitIds(visitIds);
        ReviewReadResult review = visitId == null ? null : reviewsByVisitId.get(visitId);
        return new MyReservationDetailResult(reservation, review);
    }

    public record MyReservationDetailResult(
        ReservationReadResult reservation,
        ReviewReadResult review
    ) {
    }
}
