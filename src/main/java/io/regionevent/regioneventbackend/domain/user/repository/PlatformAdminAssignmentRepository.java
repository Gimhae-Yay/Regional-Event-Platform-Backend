package io.regionevent.regioneventbackend.domain.user.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.regionevent.regioneventbackend.domain.user.entity.AppUserAccountKind;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminAssignment;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminAssignmentStatus;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminGrade;

public interface PlatformAdminAssignmentRepository extends JpaRepository<PlatformAdminAssignment, Long> {

    @Query("""
        SELECT new io.regionevent.regioneventbackend.domain.user.repository.PlatformAdminAccountListProjection(
            user.userId,
            user.loginIdentifier,
            user.name,
            assignment.grade,
            assignment.status,
            assignment.grantedAt,
            assignment.inactivatedAt
        )
        FROM PlatformAdminAssignment assignment
        JOIN assignment.appUser user
        WHERE user.accountKind = io.regionevent.regioneventbackend.domain.user.entity.AppUserAccountKind.PRIVILEGED
        ORDER BY assignment.grantedAt DESC, user.userId DESC
        """)
    List<PlatformAdminAccountListProjection> findPlatformAdminAccountList();

    @EntityGraph(attributePaths = "appUser")
    Optional<PlatformAdminAssignment> findByAppUserUserIdAndStatusAndAppUserStatusAndAppUserAccountKind(
        Long userId,
        PlatformAdminAssignmentStatus status,
        AppUserStatus appUserStatus,
        AppUserAccountKind appUserAccountKind
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "appUser")
    @Query("""
        SELECT assignment
        FROM PlatformAdminAssignment assignment
        WHERE assignment.appUser.userId = :userId
            AND assignment.status = :assignmentStatus
            AND assignment.appUser.status = :userStatus
            AND assignment.appUser.accountKind = :accountKind
        """)
    Optional<PlatformAdminAssignment> findActivePrivilegedAssignmentForUpdate(
        @Param("userId") Long userId,
        @Param("assignmentStatus") PlatformAdminAssignmentStatus assignmentStatus,
        @Param("userStatus") AppUserStatus userStatus,
        @Param("accountKind") AppUserAccountKind accountKind
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "appUser")
    Optional<PlatformAdminAssignment> findByAppUserUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<PlatformAdminAssignment> findByGradeAndStatus(
        PlatformAdminGrade grade,
        PlatformAdminAssignmentStatus status
    );
}
