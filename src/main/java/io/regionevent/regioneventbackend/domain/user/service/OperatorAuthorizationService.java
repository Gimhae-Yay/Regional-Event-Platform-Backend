package io.regionevent.regioneventbackend.domain.user.service;

import org.springframework.stereotype.Service;

import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
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

    public AuthorizedOperator requireAuthorizedOperator(Long userId) {
        UserRoleAssignment assignment = userRoleAssignmentRepository
            .findByIdUserIdAndIdRoleAndAppUserStatus(
                userId,
                UserRole.OPERATOR,
                AppUserStatus.ACTIVE
            )
            .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));

        Region assignedRegion = assignment.getRegion();
        if (assignedRegion == null || assignedRegion.getRegionId() == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return new AuthorizedOperator(assignment.getAppUser(), assignedRegion, assignment);
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
            if (roleAssignment == null || roleAssignment.getId() == null) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
        }
    }
}
