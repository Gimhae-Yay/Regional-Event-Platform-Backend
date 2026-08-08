package io.regionevent.regioneventbackend.domain.user.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignmentStatus;

public interface UserRoleAssignmentRepository extends JpaRepository<UserRoleAssignment, Long> {

    @EntityGraph(attributePaths = "region")
    List<UserRoleAssignment> findAllByAppUserUserIdAndStatus(
        Long userId,
        UserRoleAssignmentStatus status
    );

    @EntityGraph(attributePaths = "region")
    Optional<UserRoleAssignment> findByAppUserUserIdAndRoleAndStatusAndAppUserStatus(
        Long userId,
        UserRole role,
        UserRoleAssignmentStatus status,
        AppUserStatus appUserStatus
    );

    @Query("""
        SELECT COUNT(assignment)
        FROM UserRoleAssignment assignment
        WHERE assignment.region.regionId = :regionId
          AND assignment.role = io.regionevent.regioneventbackend.domain.user.entity.UserRole.REGION_ADMIN
          AND assignment.status = io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignmentStatus.ACTIVE
        """)
    long countActiveRegionAdminsByRegionRegionId(@Param("regionId") Long regionId);

    void deleteByAppUserUserId(Long userId);
}
