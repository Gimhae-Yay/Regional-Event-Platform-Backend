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

import io.regionevent.regioneventbackend.domain.review.entity.ReviewStatus;
import io.regionevent.regioneventbackend.domain.review.service.ReviewService;
import io.regionevent.regioneventbackend.domain.review.service.ReviewService.ReviewReadResult;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.service.AppUserService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class GetMyReservationsUseCaseTest {

    private static final Long USER_ID = 7L;

    @Test
    void 활성_회원이면_본인_예약_목록_읽기_결과를_반환한다() {
        AppUserService appUserService = mock(AppUserService.class);
        ReservationReadService reservationReadService = mock(ReservationReadService.class);
        ReviewService reviewService = mock(ReviewService.class);
        AppUser user = mock(AppUser.class);
        ReservationReadResult reservation = mock(ReservationReadResult.class);
        ReservationReadResult reservationWithoutVisit = mock(ReservationReadResult.class);
        ReservationReadIntegrityValidator.CheckInInfo checkIn =
            new ReservationReadIntegrityValidator.CheckInInfo(
                11L,
                true,
                Instant.parse("2026-08-02T01:05:00Z")
            );
        ReviewReadResult review = new ReviewReadResult(
            21L,
            11L,
            ReviewStatus.PUBLISHED,
            5,
            "좋은 체험이었습니다.",
            Instant.parse("2026-08-03T00:00:00Z"),
            Instant.parse("2026-08-03T00:00:00Z")
        );
        when(user.getUserId()).thenReturn(USER_ID);
        when(appUserService.findActiveUser(USER_ID)).thenReturn(user);
        when(reservation.checkIn()).thenReturn(checkIn);
        when(reservationWithoutVisit.checkIn()).thenReturn(
            new ReservationReadIntegrityValidator.CheckInInfo(null, false, null)
        );
        when(reservationReadService.findAllOwnedByUserId(USER_ID)).thenReturn(List.of(
            reservation,
            reservationWithoutVisit
        ));
        when(reviewService.findAllByVisitIds(List.of(11L))).thenReturn(Map.of(11L, review));
        GetMyReservationsUseCase useCase = new GetMyReservationsUseCase(
            appUserService,
            reservationReadService,
            reviewService
        );

        List<GetMyReservationsUseCase.MyReservationListResult> actual = useCase.findAll(USER_ID);

        assertThat(actual).containsExactly(
            new GetMyReservationsUseCase.MyReservationListResult(reservation, review),
            new GetMyReservationsUseCase.MyReservationListResult(reservationWithoutVisit, null)
        );
        verify(appUserService).findActiveUser(USER_ID);
        verify(reservationReadService).findAllOwnedByUserId(USER_ID);
        verify(reviewService).findAllByVisitIds(List.of(11L));
    }

    @Test
    void 활성_회원이_아니면_예약을_조회하지_않는다() {
        AppUserService appUserService = mock(AppUserService.class);
        ReservationReadService reservationReadService = mock(ReservationReadService.class);
        ReviewService reviewService = mock(ReviewService.class);
        when(appUserService.findActiveUser(USER_ID)).thenThrow(
            new BusinessException(ErrorCode.FORBIDDEN)
        );
        GetMyReservationsUseCase useCase = new GetMyReservationsUseCase(
            appUserService,
            reservationReadService,
            reviewService
        );

        assertThatThrownBy(() -> useCase.findAll(USER_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN)
            );

        verify(appUserService).findActiveUser(USER_ID);
        verifyNoInteractions(reservationReadService, reviewService);
    }
}
