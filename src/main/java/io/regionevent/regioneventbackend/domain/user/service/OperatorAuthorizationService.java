package io.regionevent.regioneventbackend.domain.user.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignmentStatus;
import io.regionevent.regioneventbackend.domain.user.repository.UserRoleAssignmentRepository;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class OperatorAuthorizationService {

    private final UserRoleAssignmentRepository userRoleAssignmentRepository;

    public OperatorAuthorizationService(
        UserRoleAssignmentRepository userRoleAssignmentRepository
    ) {
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
    }

    @Transactional(readOnly = true)
    public AuthorizedOperator requireAuthorizedOperator(Long userId) {
        return toAuthorizedOperator(userRoleAssignmentRepository
            .findByAppUserUserIdAndRoleAndStatusAndAppUserStatus(
                userId,
                UserRole.OPERATOR,
                UserRoleAssignmentStatus.ACTIVE,
                AppUserStatus.ACTIVE
            ));
    }

    @Transactional
    public AuthorizedOperator requireAuthorizedOperatorForUpdate(Long userId) {
        return toAuthorizedOperator(userRoleAssignmentRepository
            .findByAppUserUserIdAndRoleAndStatusAndAppUserStatusForUpdate(
                userId,
                UserRole.OPERATOR,
                UserRoleAssignmentStatus.ACTIVE,
                AppUserStatus.ACTIVE
            ));
    }

    private AuthorizedOperator toAuthorizedOperator(Optional<UserRoleAssignment> assignment) {
        UserRoleAssignment authorizedAssignment = assignment
            .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
        Region assignedRegion = authorizedAssignment.getRegion();
        if (assignedRegion == null || assignedRegion.getRegionId() == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return new AuthorizedOperator(
            authorizedAssignment.getAppUser(),
            assignedRegion,
            authorizedAssignment
        );
    }

    @Transactional(readOnly = true)
    public AuthorizedOperator authorizeOwnedContent(
        Long userId,
        AppUser contentOperator,
        Region contentRegion
    ) {
        AuthorizedOperator operator = requireAuthorizedOperator(userId);
        if (contentOperator == null
            || contentOperator.getUserId() == null
            || contentRegion == null
            || contentRegion.getRegionId() == null
            || !operator.user().getUserId().equals(contentOperator.getUserId())
            || !operator.region().getRegionId().equals(contentRegion.getRegionId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return operator;
    }

    public record AuthorizedOperator(
        AppUser user,
        Region region,
        UserRoleAssignment roleAssignment
    ) {

        public AuthorizedOperator {
            if (user == null || user.getUserId() == null) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
            if (region == null || region.getRegionId() == null) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
            if (roleAssignment == null || roleAssignment.getRoleAssignmentId() == null) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
        }
    }
}
