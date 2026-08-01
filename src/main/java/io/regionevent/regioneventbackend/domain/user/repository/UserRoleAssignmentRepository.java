package io.regionevent.regioneventbackend.domain.user.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignmentId;

public interface UserRoleAssignmentRepository extends JpaRepository<UserRoleAssignment, UserRoleAssignmentId> {

    List<UserRoleAssignment> findAllByIdUserId(Long userId);

    @EntityGraph(attributePaths = "region")
    Optional<UserRoleAssignment> findByIdUserIdAndIdRoleAndAppUserStatus(
        Long userId,
        UserRole role,
        AppUserStatus appUserStatus
    );
}
