package io.regionevent.regioneventbackend.domain.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

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
        AppUser user = mock(AppUser.class);
        List<ReservationReadResult> expected = List.of(mock(ReservationReadResult.class));
        when(user.getUserId()).thenReturn(USER_ID);
        when(appUserService.findActiveUser(USER_ID)).thenReturn(user);
        when(reservationReadService.findAllOwnedByUserId(USER_ID)).thenReturn(expected);
        GetMyReservationsUseCase useCase = new GetMyReservationsUseCase(
            appUserService,
            reservationReadService
        );

        List<ReservationReadResult> actual = useCase.findAll(USER_ID);

        assertThat(actual).isSameAs(expected);
        verify(appUserService).findActiveUser(USER_ID);
        verify(reservationReadService).findAllOwnedByUserId(USER_ID);
    }

    @Test
    void 활성_회원이_아니면_예약을_조회하지_않는다() {
        AppUserService appUserService = mock(AppUserService.class);
        ReservationReadService reservationReadService = mock(ReservationReadService.class);
        when(appUserService.findActiveUser(USER_ID)).thenThrow(
            new BusinessException(ErrorCode.FORBIDDEN)
        );
        GetMyReservationsUseCase useCase = new GetMyReservationsUseCase(
            appUserService,
            reservationReadService
        );

        assertThatThrownBy(() -> useCase.findAll(USER_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN)
            );

        verify(appUserService).findActiveUser(USER_ID);
        verifyNoInteractions(reservationReadService);
    }
}
