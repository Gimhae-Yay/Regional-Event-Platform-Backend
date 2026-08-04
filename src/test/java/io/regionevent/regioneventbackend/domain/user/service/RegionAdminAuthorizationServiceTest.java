package io.regionevent.regioneventbackend.domain.user.service;

import static org.junit.jupiter.api.Assertions.assertAll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.repository.UserRoleAssignmentRepository;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class RegionAdminAuthorizationServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long GIMHAE_REGION_ID = 10L;
    private static final Long DONGHAE_REGION_ID = 20L;

    private final UserRoleAssignmentRepository userRoleAssignmentRepository =
        mock(UserRoleAssignmentRepository.class);
    private final RegionAdminAuthorizationService authorizationService =
        new RegionAdminAuthorizationService(userRoleAssignmentRepository);

    @Test
    void 전체_단위_계약을_보존한다() {
        assertAll(
            () -> new RegionAdminAuthorizationServiceTest().requireAuthorizedRegionId_whenUserDoesNotExist_throwsForbidden(),
            () -> new RegionAdminAuthorizationServiceTest().requireAuthorizedRegionId_whenUserIsWithdrawing_throwsForbidden(),
            () -> new RegionAdminAuthorizationServiceTest().requireAuthorizedRegionId_whenRegionAdminRoleIsMissing_throwsForbidden(),
            () -> new RegionAdminAuthorizationServiceTest().requireAuthorizedRegionId_whenAssignedRegionIsMissing_throwsForbidden(),
            () -> new RegionAdminAuthorizationServiceTest().requireAuthorizedRegionId_whenAssignedRegionIdIsMissing_throwsForbidden(),
            () -> new RegionAdminAuthorizationServiceTest().requireAuthorizedRegionId_whenActiveRegionAdminIsAssigned_returnsRegionId(),
            () -> new RegionAdminAuthorizationServiceTest().authorize_whenAssignedRegionDiffersFromTarget_throwsForbidden(),
            () -> new RegionAdminAuthorizationServiceTest().authorize_whenActiveRegionAdminMatchesTargetRegion_completesNormally()
        );
    }

    void requireAuthorizedRegionId_whenUserDoesNotExist_throwsForbidden() {
        givenAuthorizationAssignment(Optional.empty());

        assertForbidden(() -> authorizationService.requireAuthorizedRegionId(USER_ID));
    }

    void requireAuthorizedRegionId_whenUserIsWithdrawing_throwsForbidden() {
        givenAuthorizationAssignment(Optional.empty());

        assertForbidden(() -> authorizationService.requireAuthorizedRegionId(USER_ID));
    }

    void requireAuthorizedRegionId_whenRegionAdminRoleIsMissing_throwsForbidden() {
        givenAuthorizationAssignment(Optional.empty());

        assertForbidden(() -> authorizationService.requireAuthorizedRegionId(USER_ID));
    }

    void requireAuthorizedRegionId_whenAssignedRegionIsMissing_throwsForbidden() {
        UserRoleAssignment assignment = mock(UserRoleAssignment.class);
        givenAuthorizationAssignment(Optional.of(assignment));
        when(assignment.getRegion()).thenReturn(null);

        assertForbidden(() -> authorizationService.requireAuthorizedRegionId(USER_ID));
    }

    void requireAuthorizedRegionId_whenAssignedRegionIdIsMissing_throwsForbidden() {
        UserRoleAssignment assignment = assignmentInRegion(null);
        givenAuthorizationAssignment(Optional.of(assignment));

        assertForbidden(() -> authorizationService.requireAuthorizedRegionId(USER_ID));
    }

    void requireAuthorizedRegionId_whenActiveRegionAdminIsAssigned_returnsRegionId() {
        UserRoleAssignment assignment = assignmentInRegion(GIMHAE_REGION_ID);
        givenAuthorizationAssignment(Optional.of(assignment));

        Long authorizedRegionId = authorizationService.requireAuthorizedRegionId(USER_ID);

        assertThat(authorizedRegionId).isEqualTo(GIMHAE_REGION_ID);
    }

    void authorize_whenAssignedRegionDiffersFromTarget_throwsForbidden() {
        UserRoleAssignment assignment = assignmentInRegion(DONGHAE_REGION_ID);
        givenAuthorizationAssignment(Optional.of(assignment));

        assertForbidden(() -> authorizationService.authorize(USER_ID, GIMHAE_REGION_ID));
    }

    void authorize_whenActiveRegionAdminMatchesTargetRegion_completesNormally() {
        UserRoleAssignment assignment = assignmentInRegion(GIMHAE_REGION_ID);
        givenAuthorizationAssignment(Optional.of(assignment));

        assertThat(authorizationService.authorize(USER_ID, GIMHAE_REGION_ID)).isSameAs(assignment);
    }

    private void givenAuthorizationAssignment(Optional<UserRoleAssignment> assignment) {
        when(userRoleAssignmentRepository.findByIdUserIdAndIdRoleAndAppUserStatus(
            USER_ID,
            UserRole.REGION_ADMIN,
            AppUserStatus.ACTIVE
        )).thenReturn(assignment);
    }

    private UserRoleAssignment assignmentInRegion(Long regionId) {
        UserRoleAssignment assignment = mock(UserRoleAssignment.class);
        Region region = mock(Region.class);
        when(region.getRegionId()).thenReturn(regionId);
        when(assignment.getRegion()).thenReturn(region);
        return assignment;
    }

    private void assertForbidden(Runnable action) {
        assertThatThrownBy(action::run)
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN)
            );
    }
}
