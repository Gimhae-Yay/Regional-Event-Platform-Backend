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
public class GetMyReservationsUseCase {

    private final AppUserService appUserService;
    private final ReservationReadService reservationReadService;
    private final ReviewService reviewService;

    public GetMyReservationsUseCase(
        AppUserService appUserService,
        ReservationReadService reservationReadService,
        ReviewService reviewService
    ) {
        this.appUserService = appUserService;
        this.reservationReadService = reservationReadService;
        this.reviewService = reviewService;
    }

    @Transactional(readOnly = true)
    public List<MyReservationListResult> findAll(Long userId) {
        AppUser user = appUserService.findActiveUser(userId);
        List<ReservationReadResult> reservations = reservationReadService.findAllOwnedByUserId(user.getUserId());
        List<Long> visitIds = reservations.stream()
            .map(ReservationReadResult::checkIn)
            .map(ReservationReadIntegrityValidator.CheckInInfo::visitId)
            .filter(visitId -> visitId != null)
            .toList();
        Map<Long, ReviewReadResult> reviewsByVisitId = reviewService.findAllByVisitIds(visitIds);
        return reservations.stream()
            .map(reservation -> {
                Long visitId = reservation.checkIn().visitId();
                ReviewReadResult review = visitId == null ? null : reviewsByVisitId.get(visitId);
                return new MyReservationListResult(reservation, review);
            })
            .toList();
    }

    public record MyReservationListResult(
        ReservationReadResult reservation,
        ReviewReadResult review
    ) {
    }
}
