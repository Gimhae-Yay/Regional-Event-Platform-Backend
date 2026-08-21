package io.regionevent.regioneventbackend.domain.platformadmin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminAssignmentStatus;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminGrade;
import io.regionevent.regioneventbackend.domain.user.repository.PlatformAdminAccountListProjection;
import io.regionevent.regioneventbackend.domain.user.service.PlatformAdminAssignmentService;
import io.regionevent.regioneventbackend.domain.user.service.PlatformAdminAuthorizationService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class GetAdminAccountsUseCaseTest {

    private static final Long ACTOR_USER_ID = 100L;

    private final PlatformAdminAuthorizationService authorizationService =
        mock(PlatformAdminAuthorizationService.class);
    private final PlatformAdminAssignmentService assignmentService =
        mock(PlatformAdminAssignmentService.class);
    private final GetAdminAccountsUseCase useCase = new GetAdminAccountsUseCase(
        authorizationService,
        assignmentService
    );

    @Test
    void get_인가된전체관리자_연결된고권한계정배정목록을반환한다() {
        List<PlatformAdminAccountListProjection> projections = List.of(
            new PlatformAdminAccountListProjection(
                102L,
                "platform-admin@example.com",
                "플랫폼 관리자",
                PlatformAdminGrade.PLATFORM_ADMIN,
                PlatformAdminAssignmentStatus.INACTIVE,
                Instant.parse("2026-08-20T02:00:00Z"),
                Instant.parse("2026-08-20T03:00:00Z")
            ),
            new PlatformAdminAccountListProjection(
                101L,
                "super-admin@example.com",
                "슈퍼 관리자",
                PlatformAdminGrade.SUPER_ADMIN,
                PlatformAdminAssignmentStatus.ACTIVE,
                Instant.parse("2026-08-19T02:00:00Z"),
                null
            )
        );
        when(assignmentService.findPlatformAdminAccountList()).thenReturn(projections);

        List<AdminAccountListInfo> result = useCase.get(ACTOR_USER_ID);

        assertThat(result).containsExactly(
            new AdminAccountListInfo(
                102L,
                "platform-admin@example.com",
                "플랫폼 관리자",
                PlatformAdminGrade.PLATFORM_ADMIN,
                PlatformAdminAssignmentStatus.INACTIVE,
                Instant.parse("2026-08-20T02:00:00Z"),
                Instant.parse("2026-08-20T03:00:00Z")
            ),
            new AdminAccountListInfo(
                101L,
                "super-admin@example.com",
                "슈퍼 관리자",
                PlatformAdminGrade.SUPER_ADMIN,
                PlatformAdminAssignmentStatus.ACTIVE,
                Instant.parse("2026-08-19T02:00:00Z"),
                null
            )
        );
        verify(authorizationService).requireAuthorizedSuperAdmin(ACTOR_USER_ID);
        verify(assignmentService).findPlatformAdminAccountList();
    }

    @Test
    void get_조회결과가없으면_빈목록을반환한다() {
        when(assignmentService.findPlatformAdminAccountList()).thenReturn(List.of());

        assertThat(useCase.get(ACTOR_USER_ID)).isEmpty();
    }

    @Test
    void get_활성PRIVILEGED계정이아니면_조회하지않고권한오류를전파한다() {
        when(authorizationService.requireAuthorizedSuperAdmin(ACTOR_USER_ID))
            .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        assertThatThrownBy(() -> useCase.get(ACTOR_USER_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN)
            );
        verify(assignmentService, never()).findPlatformAdminAccountList();
    }
}
