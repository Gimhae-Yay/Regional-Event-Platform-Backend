package io.regionevent.regioneventbackend.domain.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.reservation.service.GetMyReservationUseCase.MyReservationDetailResult;
import io.regionevent.regioneventbackend.domain.review.entity.ReviewStatus;
import io.regionevent.regioneventbackend.domain.review.service.ReviewService;
import io.regionevent.regioneventbackend.domain.review.service.ReviewService.ReviewReadResult;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.service.AppUserService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class GetMyReservationUseCaseTest {

    private static final Long USER_ID = 7L;
    private static final Long RESERVATION_ID = 11L;
    private static final Long VISIT_ID = 13L;

    @Test
    void find_whenActiveUserOwnsCheckedInReservation_returnsReviewResult() {
        AppUserService appUserService = mock(AppUserService.class);
        ReservationReadService reservationReadService = mock(ReservationReadService.class);
        ReviewService reviewService = mock(ReviewService.class);
        AppUser user = mock(AppUser.class);
        ReservationReadResult reservation = mock(ReservationReadResult.class);
        ReviewReadResult review = new ReviewReadResult(
            17L,
            VISIT_ID,
            ReviewStatus.PUBLISHED,
            5,
            "좋은 체험이었습니다.",
            Instant.parse("2026-08-03T00:00:00Z"),
            Instant.parse("2026-08-04T00:00:00Z")
        );
        when(user.getUserId()).thenReturn(USER_ID);
        when(appUserService.findActiveUser(USER_ID)).thenReturn(user);
        when(reservation.checkIn()).thenReturn(
            new ReservationReadIntegrityValidator.CheckInInfo(
                VISIT_ID,
                true,
                Instant.parse("2026-08-02T01:05:00Z")
            )
        );
        when(reservationReadService.findOwnedByReservationId(USER_ID, RESERVATION_ID)).thenReturn(reservation);
        when(reviewService.findAllByVisitIds(List.of(VISIT_ID))).thenReturn(Map.of(VISIT_ID, review));
        GetMyReservationUseCase useCase = new GetMyReservationUseCase(
            appUserService,
            reservationReadService,
            reviewService
        );

        MyReservationDetailResult actual = useCase.find(USER_ID, RESERVATION_ID);

        assertThat(actual).isEqualTo(new MyReservationDetailResult(reservation, review));
        verify(appUserService).findActiveUser(USER_ID);
        verify(reservationReadService).findOwnedByReservationId(USER_ID, RESERVATION_ID);
        verify(reviewService).findAllByVisitIds(List.of(VISIT_ID));
    }

    @Test
    void find_whenCheckedInReservationHasNoReview_returnsNullReview() {
        AppUserService appUserService = mock(AppUserService.class);
        ReservationReadService reservationReadService = mock(ReservationReadService.class);
        ReviewService reviewService = mock(ReviewService.class);
        AppUser user = mock(AppUser.class);
        ReservationReadResult reservation = mock(ReservationReadResult.class);
        when(user.getUserId()).thenReturn(USER_ID);
        when(appUserService.findActiveUser(USER_ID)).thenReturn(user);
        when(reservation.checkIn()).thenReturn(
            new ReservationReadIntegrityValidator.CheckInInfo(VISIT_ID, true, Instant.EPOCH)
        );
        when(reservationReadService.findOwnedByReservationId(USER_ID, RESERVATION_ID)).thenReturn(reservation);
        when(reviewService.findAllByVisitIds(List.of(VISIT_ID))).thenReturn(Map.of());
        GetMyReservationUseCase useCase = new GetMyReservationUseCase(
            appUserService,
            reservationReadService,
            reviewService
        );

        MyReservationDetailResult actual = useCase.find(USER_ID, RESERVATION_ID);

        assertThat(actual).isEqualTo(new MyReservationDetailResult(reservation, null));
        verify(reviewService).findAllByVisitIds(List.of(VISIT_ID));
    }

    @Test
    void find_whenReservationHasNoVisit_returnsNullReviewWithEmptyVisitIds() {
        AppUserService appUserService = mock(AppUserService.class);
        ReservationReadService reservationReadService = mock(ReservationReadService.class);
        ReviewService reviewService = mock(ReviewService.class);
        AppUser user = mock(AppUser.class);
        ReservationReadResult reservation = mock(ReservationReadResult.class);
        when(user.getUserId()).thenReturn(USER_ID);
        when(appUserService.findActiveUser(USER_ID)).thenReturn(user);
        when(reservation.checkIn()).thenReturn(
            new ReservationReadIntegrityValidator.CheckInInfo(null, false, null)
        );
        when(reservationReadService.findOwnedByReservationId(USER_ID, RESERVATION_ID)).thenReturn(reservation);
        when(reviewService.findAllByVisitIds(List.of())).thenReturn(Map.of());
        GetMyReservationUseCase useCase = new GetMyReservationUseCase(
            appUserService,
            reservationReadService,
            reviewService
        );

        MyReservationDetailResult actual = useCase.find(USER_ID, RESERVATION_ID);

        assertThat(actual).isEqualTo(new MyReservationDetailResult(reservation, null));
        verify(reviewService).findAllByVisitIds(List.of());
    }

    @Test
    void find_whenUserIsNotActive_doesNotReadReservation() {
        AppUserService appUserService = mock(AppUserService.class);
        ReservationReadService reservationReadService = mock(ReservationReadService.class);
        ReviewService reviewService = mock(ReviewService.class);
        when(appUserService.findActiveUser(USER_ID)).thenThrow(
            new BusinessException(ErrorCode.FORBIDDEN)
        );
        GetMyReservationUseCase useCase = new GetMyReservationUseCase(
            appUserService,
            reservationReadService,
            reviewService
        );

        assertThatThrownBy(() -> useCase.find(USER_ID, RESERVATION_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN)
            );

        verify(appUserService).findActiveUser(USER_ID);
        verifyNoInteractions(reservationReadService, reviewService);
    }

    @Test
    void find_whenReservationReadFails_doesNotReadReview() {
        AppUserService appUserService = mock(AppUserService.class);
        ReservationReadService reservationReadService = mock(ReservationReadService.class);
        ReviewService reviewService = mock(ReviewService.class);
        AppUser user = mock(AppUser.class);
        when(user.getUserId()).thenReturn(USER_ID);
        when(appUserService.findActiveUser(USER_ID)).thenReturn(user);
        when(reservationReadService.findOwnedByReservationId(USER_ID, RESERVATION_ID)).thenThrow(
            new BusinessException(ErrorCode.NOT_FOUND)
        );
        GetMyReservationUseCase useCase = new GetMyReservationUseCase(
            appUserService,
            reservationReadService,
            reviewService
        );

        assertThatThrownBy(() -> useCase.find(USER_ID, RESERVATION_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND)
            );

        verify(reservationReadService).findOwnedByReservationId(USER_ID, RESERVATION_ID);
        verifyNoInteractions(reviewService);
    }
}
