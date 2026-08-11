package io.regionevent.regioneventbackend.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.payment.entity.Refund;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.service.AppUserService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class GetMyRefundsUseCaseTest {

    private static final Long USER_ID = 7L;

    @Test
    void findAll_whenActiveUser_returnsOnlyRefundsFoundByOwnerId() {
        AppUserService appUserService = mock(AppUserService.class);
        RefundService refundService = mock(RefundService.class);
        AppUser user = mock(AppUser.class);
        List<Refund> expected = List.of(mock(Refund.class), mock(Refund.class));
        when(user.getUserId()).thenReturn(USER_ID);
        when(appUserService.findActiveUser(USER_ID)).thenReturn(user);
        when(refundService.findAllOwnedByUserId(USER_ID)).thenReturn(expected);
        GetMyRefundsUseCase useCase = new GetMyRefundsUseCase(appUserService, refundService);

        List<Refund> actual = useCase.findAll(USER_ID);

        assertThat(actual).isSameAs(expected);
        verify(appUserService).findActiveUser(USER_ID);
        verify(refundService).findAllOwnedByUserId(USER_ID);
    }

    @Test
    void findAll_whenUserIsNotActive_doesNotReadRefunds() {
        AppUserService appUserService = mock(AppUserService.class);
        RefundService refundService = mock(RefundService.class);
        when(appUserService.findActiveUser(USER_ID)).thenThrow(
            new BusinessException(ErrorCode.FORBIDDEN)
        );
        GetMyRefundsUseCase useCase = new GetMyRefundsUseCase(appUserService, refundService);

        assertThatThrownBy(() -> useCase.findAll(USER_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN)
            );

        verify(appUserService).findActiveUser(USER_ID);
        verifyNoInteractions(refundService);
    }
}
