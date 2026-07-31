package io.regionevent.regioneventbackend.domain.user.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignmentId;
import io.regionevent.regioneventbackend.domain.user.repository.UserRoleAssignmentRepository;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class OperatorAuthorityService {

    private final UserRoleAssignmentRepository userRoleAssignmentRepository;

    public OperatorAuthorityService(UserRoleAssignmentRepository userRoleAssignmentRepository) {
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
    }

    @Transactional(readOnly = true)
    public OperatorAuthority findActiveOperatorAuthority(Long userId) {
        UserRoleAssignment assignment = userRoleAssignmentRepository.findById(
            new UserRoleAssignmentId(userId, UserRole.OPERATOR)
        ).orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));

        if (assignment.getAppUser().getStatus() != AppUserStatus.ACTIVE || assignment.getRegion() == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return new OperatorAuthority(assignment.getAppUser(), assignment.getRegion());
    }
}
