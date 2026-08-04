package io.regionevent.regioneventbackend.domain.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.service.AppUserService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class GetMyReservationUseCaseTest {

    private static final Long USER_ID = 7L;
    private static final Long RESERVATION_ID = 11L;

    @Test
    void find_whenActiveUserOwnsReservation_returnsReadResult() {
        AppUserService appUserService = mock(AppUserService.class);
        ReservationReadService reservationReadService = mock(ReservationReadService.class);
        AppUser user = mock(AppUser.class);
        ReservationReadResult expected = mock(ReservationReadResult.class);
        when(user.getUserId()).thenReturn(USER_ID);
        when(appUserService.findActiveUser(USER_ID)).thenReturn(user);
        when(reservationReadService.findOwnedByReservationId(USER_ID, RESERVATION_ID)).thenReturn(expected);
        GetMyReservationUseCase useCase = new GetMyReservationUseCase(
            appUserService,
            reservationReadService
        );

        ReservationReadResult actual = useCase.find(USER_ID, RESERVATION_ID);

        assertThat(actual).isSameAs(expected);
        verify(appUserService).findActiveUser(USER_ID);
        verify(reservationReadService).findOwnedByReservationId(USER_ID, RESERVATION_ID);
    }

    @Test
    void find_whenUserIsNotActive_doesNotReadReservation() {
        AppUserService appUserService = mock(AppUserService.class);
        ReservationReadService reservationReadService = mock(ReservationReadService.class);
        when(appUserService.findActiveUser(USER_ID)).thenThrow(
            new BusinessException(ErrorCode.FORBIDDEN)
        );
        GetMyReservationUseCase useCase = new GetMyReservationUseCase(
            appUserService,
            reservationReadService
        );

        assertThatThrownBy(() -> useCase.find(USER_ID, RESERVATION_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN)
            );

        verify(appUserService).findActiveUser(USER_ID);
        verifyNoInteractions(reservationReadService);
    }
}
