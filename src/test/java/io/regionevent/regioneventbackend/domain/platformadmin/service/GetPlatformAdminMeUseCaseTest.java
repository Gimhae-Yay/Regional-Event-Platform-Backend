package io.regionevent.regioneventbackend.domain.platformadmin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminAssignment;
import io.regionevent.regioneventbackend.domain.user.service.PlatformAdminAuthorizationService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class GetPlatformAdminMeUseCaseTest {

    private static final Long ACTOR_USER_ID = 100L;

    private final PlatformAdminAuthorizationService authorizationService =
        mock(PlatformAdminAuthorizationService.class);
    private final GetPlatformAdminMeUseCase useCase = new GetPlatformAdminMeUseCase(
        authorizationService
    );

    @Test
    void get_활성PRIVILEGED계정_인증주체식별자를반환한다() {
        when(authorizationService.requireAuthorizedPlatformAdmin(ACTOR_USER_ID))
            .thenReturn(mock(PlatformAdminAssignment.class));

        Long result = useCase.get(ACTOR_USER_ID);

        assertThat(result).isEqualTo(ACTOR_USER_ID);
        verify(authorizationService).requireAuthorizedPlatformAdmin(ACTOR_USER_ID);
    }

    @Test
    void get_DB최종인가가부족하면_권한오류를전파한다() {
        when(authorizationService.requireAuthorizedPlatformAdmin(ACTOR_USER_ID))
            .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        assertThatThrownBy(() -> useCase.get(ACTOR_USER_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN)
            );
        verify(authorizationService).requireAuthorizedPlatformAdmin(ACTOR_USER_ID);
    }
}
