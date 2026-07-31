package io.regionevent.regioneventbackend.domain.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignmentId;

public interface UserRoleAssignmentRepository extends JpaRepository<UserRoleAssignment, UserRoleAssignmentId> {

    @EntityGraph(attributePaths = "region")
    Optional<UserRoleAssignment> findByIdUserIdAndIdRoleAndAppUserStatus(
        Long userId,
        UserRole role,
        AppUserStatus appUserStatus
    );
}
