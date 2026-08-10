package io.regionevent.regioneventbackend.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.repository.PlatformAdminUserListProjection;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class GetPlatformAdminUsersUseCaseTest {

    private static final Long ACTOR_USER_ID = 100L;

    private final PlatformAdminAuthorizationService platformAdminAuthorizationService = mock(
        PlatformAdminAuthorizationService.class
    );
    private final AppUserService appUserService = mock(AppUserService.class);
    private final GetPlatformAdminUsersUseCase useCase = new GetPlatformAdminUsersUseCase(
        platformAdminAuthorizationService,
        appUserService
    );

    @Test
    void get_활성전체관리자_사용자별활성역할을그룹화해반환한다() {
        when(appUserService.findPlatformAdminUserList()).thenReturn(List.of(
            projection(UserRole.OPERATOR, 11L, "김해시"),
            projection(UserRole.REGION_ADMIN, 12L, "부산시")
        ));

        List<PlatformAdminUserListInfo> users = useCase.get(ACTOR_USER_ID);

        assertThat(users).containsExactly(new PlatformAdminUserListInfo(
            200L,
            "operator@example.com",
            "운영자",
            List.of(
                new PlatformAdminUserListInfo.RoleAssignmentInfo(UserRole.OPERATOR, 11L, "김해시"),
                new PlatformAdminUserListInfo.RoleAssignmentInfo(UserRole.REGION_ADMIN, 12L, "부산시")
            ),
            Instant.parse("2026-08-10T00:00:00Z")
        ));
        verify(platformAdminAuthorizationService).requireAuthorizedPlatformAdmin(ACTOR_USER_ID);
        verify(appUserService).findPlatformAdminUserList();
    }

    @Test
    void get_전체관리자가아니면_사용자목록을조회하지않는다() {
        when(platformAdminAuthorizationService.requireAuthorizedPlatformAdmin(ACTOR_USER_ID))
            .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        assertThatThrownBy(() -> useCase.get(ACTOR_USER_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN)
            );

        verify(appUserService, never()).findPlatformAdminUserList();
    }

    private PlatformAdminUserListProjection projection(
        UserRole role,
        Long regionId,
        String regionName
    ) {
        return new PlatformAdminUserListProjection(
            200L,
            "operator@example.com",
            "운영자",
            role,
            regionId,
            regionName,
            Instant.parse("2026-08-10T00:00:00Z")
        );
    }
}
