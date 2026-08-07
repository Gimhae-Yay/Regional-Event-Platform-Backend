package io.regionevent.regioneventbackend.domain.user.service;

import java.util.Comparator;
import java.util.List;

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
public class UserRoleAssignmentService {

    private final UserRoleAssignmentRepository userRoleAssignmentRepository;

    public UserRoleAssignmentService(UserRoleAssignmentRepository userRoleAssignmentRepository) {
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
    }

    public void assignVisitor(AppUser user) {
        userRoleAssignmentRepository.save(new UserRoleAssignment(user, UserRole.VISITOR, null));
    }

    public void assignOperator(AppUser user, Region region) {
        userRoleAssignmentRepository.save(new UserRoleAssignment(user, UserRole.OPERATOR, region));
    }

    public List<UserRole> findRolesByUserId(Long userId) {
        return findRoleAssignmentsByUserId(userId)
            .stream()
            .map(UserRoleAssignment::getRole)
            .toList();
    }

    public List<UserRoleAssignment> findRoleAssignmentsByUserId(Long userId) {
        return userRoleAssignmentRepository.findAllByIdUserId(userId)
            .stream()
            .sorted(Comparator.comparing(UserRoleAssignment::getRole))
            .toList();
    }

    public UserRoleAssignment findActiveVisitor(Long userId) {
        return userRoleAssignmentRepository.findByIdUserIdAndIdRoleAndAppUserStatus(
            userId,
            UserRole.VISITOR,
            AppUserStatus.ACTIVE
        ).orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
    }

    public UserRoleAssignment findActiveOperator(Long userId) {
        return userRoleAssignmentRepository.findByIdUserIdAndIdRoleAndAppUserStatus(
            userId,
            UserRole.OPERATOR,
            AppUserStatus.ACTIVE
        ).orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
    }

    public boolean hasPrivilegedRole(Long userId) {
        List<UserRole> roles = findRolesByUserId(userId);
        return roles.contains(UserRole.OPERATOR) || roles.contains(UserRole.REGION_ADMIN);
    }

    public void deleteAllByUserId(Long userId) {
        userRoleAssignmentRepository.deleteByIdUserId(userId);
    }
}
