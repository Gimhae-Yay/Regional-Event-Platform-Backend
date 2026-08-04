package io.regionevent.regioneventbackend.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignmentId;
import io.regionevent.regioneventbackend.domain.user.repository.UserRoleAssignmentRepository;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class OperatorAuthorizationServiceTest {

    private static final Long OPERATOR_USER_ID = 1L;
    private static final Long OTHER_OPERATOR_USER_ID = 2L;
    private static final Long GIMHAE_REGION_ID = 10L;
    private static final Long DONGHAE_REGION_ID = 20L;

    private final UserRoleAssignmentRepository userRoleAssignmentRepository =
        mock(UserRoleAssignmentRepository.class);
    private final OperatorAuthorizationService operatorAuthorizationService =
        new OperatorAuthorizationService(userRoleAssignmentRepository);

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

    private void givenAuthorizationAssignment(Optional<UserRoleAssignment> assignment) {
        when(userRoleAssignmentRepository.findByIdUserIdAndIdRoleAndAppUserStatus(
            OPERATOR_USER_ID,
            UserRole.OPERATOR,
            AppUserStatus.ACTIVE
        )).thenReturn(assignment);
    }

    private UserRoleAssignment assignment(Long userId, Long regionId) {
        UserRoleAssignment assignment = mock(UserRoleAssignment.class);
        AppUser appUser = user(userId);
        Region assignedRegion = regionId == null ? null : region(regionId);
        when(assignment.getAppUser()).thenReturn(appUser);
        when(assignment.getRegion()).thenReturn(assignedRegion);
        when(assignment.getId()).thenReturn(new UserRoleAssignmentId(userId, UserRole.OPERATOR));
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
