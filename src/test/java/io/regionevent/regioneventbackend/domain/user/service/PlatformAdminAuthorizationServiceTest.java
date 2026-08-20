package io.regionevent.regioneventbackend.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;

import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserAccountKind;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminAssignment;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminGrade;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class PlatformAdminAuthorizationServiceTest {

    private static final Long USER_ID = 1L;

    private final AppUserRepository appUserRepository = mock(AppUserRepository.class);
    private final PlatformAdminAuthorizationService platformAdminAuthorizationService =
        new PlatformAdminAuthorizationService(appUserRepository);

    @ParameterizedTest
    @EnumSource(value = PlatformAdminGrade.class)
    void 활성_PRIVILEGED_계정의_고권한_배정은_전체관리자_인가를_통과한다(PlatformAdminGrade grade) {
        PlatformAdminAssignment assignment = assignment(grade);
        givenActiveAssignment(Optional.of(assignment));

        assertThat(platformAdminAuthorizationService.requireAuthorizedPlatformAdmin(USER_ID))
            .isSameAs(assignment);
        verifyActiveAssignmentLookup();
    }

    @Test
    void 고권한_배정_이력이_없으면_전체관리자_인가를_거부한다() {
        givenActiveAssignment(Optional.empty());

        assertForbidden(() -> platformAdminAuthorizationService.requireAuthorizedPlatformAdmin(USER_ID));
    }

    @Test
    void 슈퍼관리자_전용_등급은_SecurityConfig에서_검증하고_DB인가서비스는_활성_PRIVILEGED_계정만_확인한다() {
        PlatformAdminAssignment assignment = assignment(PlatformAdminGrade.PLATFORM_ADMIN);
        givenActiveAssignment(Optional.of(assignment));

        assertThat(platformAdminAuthorizationService.requireAuthorizedSuperAdmin(USER_ID))
            .isSameAs(assignment);
        verifyActiveAssignmentLookup();
    }

    @Test
    void 인증_주체가_없으면_조회하지_않고_인가를_거부한다() {
        assertForbidden(() -> platformAdminAuthorizationService.requireAuthorizedPlatformAdmin(null));

        verifyNoInteractions(appUserRepository);
    }

    @Test
    void 수정용_전체관리자_인가는_계정과고권한배정을순서대로잠근다() {
        PlatformAdminAssignment assignment = assignment(PlatformAdminGrade.PLATFORM_ADMIN);
        givenActivePrivilegedUserForUpdate();
        givenActiveAssignmentForUpdate(Optional.of(assignment));

        assertThat(platformAdminAuthorizationService.requireAuthorizedPlatformAdminForUpdate(USER_ID))
            .isSameAs(assignment);
        InOrder inOrder = org.mockito.Mockito.inOrder(appUserRepository);
        inOrder.verify(appUserRepository).findByIdForUpdate(USER_ID);
        verifyActiveAssignmentForUpdateLookup(inOrder);
    }

    private void givenActiveAssignment(Optional<PlatformAdminAssignment> assignment) {
        when(appUserRepository.findPrivilegedAssignment(
                USER_ID,
                AppUserStatus.ACTIVE,
                AppUserAccountKind.PRIVILEGED
            ))
            .thenReturn(assignment);
    }

    private void givenActiveAssignmentForUpdate(Optional<PlatformAdminAssignment> assignment) {
        when(appUserRepository.findPrivilegedAssignmentForUpdate(
                USER_ID,
                AppUserStatus.ACTIVE,
                AppUserAccountKind.PRIVILEGED
            ))
            .thenReturn(assignment);
    }

    private void givenActivePrivilegedUserForUpdate() {
        AppUser user = mock(AppUser.class);
        when(user.getStatus()).thenReturn(AppUserStatus.ACTIVE);
        when(user.getAccountKind()).thenReturn(AppUserAccountKind.PRIVILEGED);
        when(appUserRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
    }

    private void verifyActiveAssignmentLookup() {
        verify(appUserRepository).findPrivilegedAssignment(
                USER_ID,
                AppUserStatus.ACTIVE,
                AppUserAccountKind.PRIVILEGED
            );
    }

    private void verifyActiveAssignmentForUpdateLookup(InOrder inOrder) {
        inOrder.verify(appUserRepository).findPrivilegedAssignmentForUpdate(
            USER_ID,
            AppUserStatus.ACTIVE,
            AppUserAccountKind.PRIVILEGED
        );
    }

    private PlatformAdminAssignment assignment(PlatformAdminGrade grade) {
        PlatformAdminAssignment assignment = mock(PlatformAdminAssignment.class);
        when(assignment.getGrade()).thenReturn(grade);
        return assignment;
    }

    private void assertForbidden(Runnable action) {
        assertThatThrownBy(action::run)
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN)
            );
    }
}
