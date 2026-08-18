package io.regionevent.regioneventbackend.domain.user.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

@Service
public class OperatorAuthorizationService {

    private final AppUserRepository appUserRepository;

    public OperatorAuthorizationService(
        AppUserRepository appUserRepository
    ) {
        this.appUserRepository = appUserRepository;
    }

    @Transactional(readOnly = true)
    public AuthorizedOperator requireAuthorizedOperator(Long userId) {
        validateUserId(userId);
        return toAuthorizedOperator(appUserRepository.findActiveRoleAssignment(
                userId,
                UserRole.OPERATOR,
                UserRoleAssignmentStatus.ACTIVE,
                AppUserStatus.ACTIVE,
                AppUserAccountKind.ORDINARY
            ));
    }

    @Transactional
    public AuthorizedOperator requireAuthorizedOperatorForUpdate(Long userId) {
        validateUserId(userId);
        lockActiveUser(userId);
        return toAuthorizedOperator(appUserRepository.findActiveRoleAssignmentForUpdate(
                userId,
                UserRole.OPERATOR,
                UserRoleAssignmentStatus.ACTIVE,
                AppUserStatus.ACTIVE,
                AppUserAccountKind.ORDINARY
            ));
    }

    private void lockActiveUser(Long userId) {
        appUserRepository.findByIdForUpdate(userId)
            .filter(user -> user.getStatus() == AppUserStatus.ACTIVE)
            .filter(user -> user.getAccountKind() == AppUserAccountKind.ORDINARY)
            .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
    }

    private void validateUserId(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
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
