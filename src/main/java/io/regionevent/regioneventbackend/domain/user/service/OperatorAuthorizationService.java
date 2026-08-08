package io.regionevent.regioneventbackend.domain.user.service;

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
        UserRoleAssignment assignment = userRoleAssignmentRepository
            .findByAppUserUserIdAndRoleAndStatusAndAppUserStatus(
                userId,
                UserRole.OPERATOR,
                UserRoleAssignmentStatus.ACTIVE,
                AppUserStatus.ACTIVE
            )
            .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));

        Region assignedRegion = assignment.getRegion();
        if (assignedRegion == null || assignedRegion.getRegionId() == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return new AuthorizedOperator(assignment.getAppUser(), assignedRegion, assignment);
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
