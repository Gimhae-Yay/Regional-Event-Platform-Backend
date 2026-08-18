package io.regionevent.regioneventbackend.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserAccountKind;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignmentStatus;
import io.regionevent.regioneventbackend.domain.user.repository.AppUserRepository;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class OperatorAuthorizationServiceTest {

    private static final Long OPERATOR_USER_ID = 1L;
    private static final Long OTHER_OPERATOR_USER_ID = 2L;
    private static final Long GIMHAE_REGION_ID = 10L;
    private static final Long DONGHAE_REGION_ID = 20L;

    private final AppUserRepository appUserRepository = mock(AppUserRepository.class);
    private final OperatorAuthorizationService operatorAuthorizationService =
        new OperatorAuthorizationService(appUserRepository);

    @Test
    void 소유_콘텐츠_인가_회원이_없으면_FORBIDDEN을_반환한다() {
        givenAuthorizationAssignment(Optional.empty());

        assertForbidden(() -> operatorAuthorizationService.authorizeOwnedContent(
            OPERATOR_USER_ID,
            user(OPERATOR_USER_ID),
            region(GIMHAE_REGION_ID)
        ));
    }

    @Test
    void 소유_콘텐츠_인가_회원이_활성_상태가_아니면_FORBIDDEN을_반환한다() {
        givenAuthorizationAssignment(Optional.empty());

        assertForbidden(() -> operatorAuthorizationService.authorizeOwnedContent(
            OPERATOR_USER_ID,
            user(OPERATOR_USER_ID),
            region(GIMHAE_REGION_ID)
        ));
    }

    @Test
    void 소유_콘텐츠_인가_운영자_역할이_없으면_FORBIDDEN을_반환한다() {
        givenAuthorizationAssignment(Optional.empty());

        assertForbidden(() -> operatorAuthorizationService.authorizeOwnedContent(
            OPERATOR_USER_ID,
            user(OPERATOR_USER_ID),
            region(GIMHAE_REGION_ID)
        ));
    }

    @Test
    void 소유_콘텐츠_인가_담당_지역이_없으면_FORBIDDEN을_반환한다() {
        UserRoleAssignment assignment = assignment(OPERATOR_USER_ID, null);
        givenAuthorizationAssignment(Optional.of(assignment));

        assertForbidden(() -> operatorAuthorizationService.authorizeOwnedContent(
            OPERATOR_USER_ID,
            user(OPERATOR_USER_ID),
            region(GIMHAE_REGION_ID)
        ));
    }

    @Test
    void 소유_콘텐츠_인가_콘텐츠_지역이_다르면_FORBIDDEN을_반환한다() {
        UserRoleAssignment assignment = assignment(OPERATOR_USER_ID, GIMHAE_REGION_ID);
        givenAuthorizationAssignment(Optional.of(assignment));

        assertForbidden(() -> operatorAuthorizationService.authorizeOwnedContent(
            OPERATOR_USER_ID,
            user(OPERATOR_USER_ID),
            region(DONGHAE_REGION_ID)
        ));
    }

    @Test
    void 소유_콘텐츠_인가_콘텐츠_소유_운영자가_다르면_FORBIDDEN을_반환한다() {
        UserRoleAssignment assignment = assignment(OPERATOR_USER_ID, GIMHAE_REGION_ID);
        givenAuthorizationAssignment(Optional.of(assignment));

        assertForbidden(() -> operatorAuthorizationService.authorizeOwnedContent(
            OPERATOR_USER_ID,
            user(OTHER_OPERATOR_USER_ID),
            region(GIMHAE_REGION_ID)
        ));
    }

    @Test
    void 소유_콘텐츠_인가_소유_운영자와_지역이_모두_일치하면_운영자_정보를_반환한다() {
        UserRoleAssignment assignment = assignment(OPERATOR_USER_ID, GIMHAE_REGION_ID);
        givenAuthorizationAssignment(Optional.of(assignment));

        OperatorAuthorizationService.AuthorizedOperator authorizedOperator =
            operatorAuthorizationService.authorizeOwnedContent(
                OPERATOR_USER_ID,
                user(OPERATOR_USER_ID),
                region(GIMHAE_REGION_ID)
            );

        assertThat(authorizedOperator.user().getUserId()).isEqualTo(OPERATOR_USER_ID);
        assertThat(authorizedOperator.region().getRegionId()).isEqualTo(GIMHAE_REGION_ID);
        assertThat(authorizedOperator.roleAssignment()).isSameAs(assignment);
    }

    @Test
    void 수정용_운영자_인가는_역할배정을_잠가_조회한다() {
        UserRoleAssignment assignment = assignment(OPERATOR_USER_ID, GIMHAE_REGION_ID);
        AppUser user = user(OPERATOR_USER_ID);
        when(user.getStatus()).thenReturn(AppUserStatus.ACTIVE);
        when(user.getAccountKind()).thenReturn(AppUserAccountKind.ORDINARY);
        when(appUserRepository.findByIdForUpdate(OPERATOR_USER_ID)).thenReturn(Optional.of(user));
        when(appUserRepository.findActiveRoleAssignmentForUpdate(
                OPERATOR_USER_ID,
                UserRole.OPERATOR,
                UserRoleAssignmentStatus.ACTIVE,
                AppUserStatus.ACTIVE,
                AppUserAccountKind.ORDINARY
            )).thenReturn(Optional.of(assignment));

        OperatorAuthorizationService.AuthorizedOperator authorizedOperator =
            operatorAuthorizationService.requireAuthorizedOperatorForUpdate(OPERATOR_USER_ID);

        assertThat(authorizedOperator.roleAssignment()).isSameAs(assignment);
        InOrder inOrder = org.mockito.Mockito.inOrder(appUserRepository);
        inOrder.verify(appUserRepository).findByIdForUpdate(OPERATOR_USER_ID);
        inOrder.verify(appUserRepository).findActiveRoleAssignmentForUpdate(
            OPERATOR_USER_ID,
            UserRole.OPERATOR,
            UserRoleAssignmentStatus.ACTIVE,
            AppUserStatus.ACTIVE,
            AppUserAccountKind.ORDINARY
        );
    }

    private void givenAuthorizationAssignment(Optional<UserRoleAssignment> assignment) {
        when(appUserRepository.findActiveRoleAssignment(
                OPERATOR_USER_ID,
                UserRole.OPERATOR,
                UserRoleAssignmentStatus.ACTIVE,
                AppUserStatus.ACTIVE,
                AppUserAccountKind.ORDINARY
            )).thenReturn(assignment);
    }

    private UserRoleAssignment assignment(Long userId, Long regionId) {
        UserRoleAssignment assignment = mock(UserRoleAssignment.class);
        AppUser appUser = user(userId);
        Region assignedRegion = regionId == null ? null : region(regionId);
        when(assignment.getAppUser()).thenReturn(appUser);
        when(assignment.getRegion()).thenReturn(assignedRegion);
        when(assignment.getRoleAssignmentId()).thenReturn(1L);
        return assignment;
    }

    private AppUser user(Long userId) {
        AppUser appUser = mock(AppUser.class);
        when(appUser.getUserId()).thenReturn(userId);
        return appUser;
    }

    private Region region(Long regionId) {
        Region region = mock(Region.class);
        when(region.getRegionId()).thenReturn(regionId);
        return region;
    }

    private void assertForbidden(Runnable action) {
        assertThatThrownBy(action::run)
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN)
            );
    }
}
