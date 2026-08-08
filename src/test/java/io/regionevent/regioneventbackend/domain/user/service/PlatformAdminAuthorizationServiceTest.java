package io.regionevent.regioneventbackend.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.user.entity.AppUserAccountKind;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminAssignment;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminAssignmentStatus;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminGrade;
import io.regionevent.regioneventbackend.domain.user.repository.PlatformAdminAssignmentRepository;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class PlatformAdminAuthorizationServiceTest {

    private static final Long USER_ID = 1L;

    private final PlatformAdminAssignmentRepository platformAdminAssignmentRepository =
        mock(PlatformAdminAssignmentRepository.class);
    private final PlatformAdminAuthorizationService platformAdminAuthorizationService =
        new PlatformAdminAuthorizationService(platformAdminAssignmentRepository);

    @ParameterizedTest
    @EnumSource(value = PlatformAdminGrade.class)
    void 활성_고권한_등급은_전체관리자_인가를_통과한다(PlatformAdminGrade grade) {
        PlatformAdminAssignment assignment = assignment(grade);
        givenActiveAssignment(Optional.of(assignment));

        assertThat(platformAdminAuthorizationService.requireAuthorizedPlatformAdmin(USER_ID))
            .isSameAs(assignment);
        verifyActiveAssignmentLookup();
    }

    @Test
    void 활성_고권한_배정이_없으면_전체관리자_인가를_거부한다() {
        givenActiveAssignment(Optional.empty());

        assertForbidden(() -> platformAdminAuthorizationService.requireAuthorizedPlatformAdmin(USER_ID));
    }

    @Test
    void 플랫폼관리자는_슈퍼관리자_전용_인가를_통과하지_못한다() {
        givenActiveAssignment(Optional.of(assignment(PlatformAdminGrade.PLATFORM_ADMIN)));

        assertForbidden(() -> platformAdminAuthorizationService.requireAuthorizedSuperAdmin(USER_ID));
    }

    @Test
    void 슈퍼관리자는_슈퍼관리자_전용_인가를_통과한다() {
        PlatformAdminAssignment assignment = assignment(PlatformAdminGrade.SUPER_ADMIN);
        givenActiveAssignment(Optional.of(assignment));

        assertThat(platformAdminAuthorizationService.requireAuthorizedSuperAdmin(USER_ID))
            .isSameAs(assignment);
        verifyActiveAssignmentLookup();
    }

    @Test
    void 인증_주체가_없으면_조회하지_않고_인가를_거부한다() {
        assertForbidden(() -> platformAdminAuthorizationService.requireAuthorizedPlatformAdmin(null));

        verifyNoInteractions(platformAdminAssignmentRepository);
    }

    private void givenActiveAssignment(Optional<PlatformAdminAssignment> assignment) {
        when(platformAdminAssignmentRepository
            .findByAppUserUserIdAndStatusAndAppUserStatusAndAppUserAccountKind(
                USER_ID,
                PlatformAdminAssignmentStatus.ACTIVE,
                AppUserStatus.ACTIVE,
                AppUserAccountKind.PRIVILEGED
            ))
            .thenReturn(assignment);
    }

    private void verifyActiveAssignmentLookup() {
        verify(platformAdminAssignmentRepository)
            .findByAppUserUserIdAndStatusAndAppUserStatusAndAppUserAccountKind(
                USER_ID,
                PlatformAdminAssignmentStatus.ACTIVE,
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
