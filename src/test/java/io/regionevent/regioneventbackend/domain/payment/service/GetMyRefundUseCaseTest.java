package io.regionevent.regioneventbackend.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.payment.entity.Refund;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.service.AppUserService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class GetMyRefundUseCaseTest {

    private static final Long USER_ID = 7L;
    private static final Long REFUND_ID = 11L;

    @Test
    void find_whenActiveUserOwnsRefund_returnsRefund() {
        AppUserService appUserService = mock(AppUserService.class);
        RefundService refundService = mock(RefundService.class);
        AppUser user = mock(AppUser.class);
        Refund expected = mock(Refund.class);
        when(user.getUserId()).thenReturn(USER_ID);
        when(appUserService.findActiveUser(USER_ID)).thenReturn(user);
        when(refundService.findOwnedByRefundId(USER_ID, REFUND_ID)).thenReturn(expected);
        GetMyRefundUseCase useCase = new GetMyRefundUseCase(appUserService, refundService);

        Refund actual = useCase.find(USER_ID, REFUND_ID);

        assertThat(actual).isSameAs(expected);
        verify(appUserService).findActiveUser(USER_ID);
        verify(refundService).findOwnedByRefundId(USER_ID, REFUND_ID);
    }

    @Test
    void find_whenRefundIsNotOwned_throwsNotFoundWithoutChangingRefund() {
        AppUserService appUserService = mock(AppUserService.class);
        RefundService refundService = mock(RefundService.class);
        AppUser user = mock(AppUser.class);
        when(user.getUserId()).thenReturn(USER_ID);
        when(appUserService.findActiveUser(USER_ID)).thenReturn(user);
        when(refundService.findOwnedByRefundId(USER_ID, REFUND_ID)).thenThrow(
            new BusinessException(ErrorCode.NOT_FOUND)
        );
        GetMyRefundUseCase useCase = new GetMyRefundUseCase(appUserService, refundService);

        assertThatThrownBy(() -> useCase.find(USER_ID, REFUND_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND)
            );

        verify(appUserService).findActiveUser(USER_ID);
        verify(refundService).findOwnedByRefundId(USER_ID, REFUND_ID);
    }

    @Test
    void find_whenUserIsNotActive_doesNotReadRefund() {
        AppUserService appUserService = mock(AppUserService.class);
        RefundService refundService = mock(RefundService.class);
        when(appUserService.findActiveUser(USER_ID)).thenThrow(
            new BusinessException(ErrorCode.FORBIDDEN)
        );
        GetMyRefundUseCase useCase = new GetMyRefundUseCase(appUserService, refundService);

        assertThatThrownBy(() -> useCase.find(USER_ID, REFUND_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN)
            );

        verify(appUserService).findActiveUser(USER_ID);
        verifyNoInteractions(refundService);
    }
}
