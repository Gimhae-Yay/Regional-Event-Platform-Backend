package io.regionevent.regioneventbackend.domain.user.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserAccountKind;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminAssignment;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminAssignmentStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignmentStatus;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    boolean existsByLoginIdentifier(String loginIdentifier);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AppUser> findByLoginIdentifier(String loginIdentifier);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from AppUser user where user.userId = ?1")
    Optional<AppUser> findByIdForUpdate(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT user
        FROM AppUser user
        WHERE user.userId IN :userIds
        ORDER BY user.userId ASC
        """)
    List<AppUser> findAllByUserIdInForUpdate(@Param("userIds") List<Long> userIds);

    @Query("""
        SELECT assignment
        FROM PlatformAdminAssignment assignment
        JOIN FETCH assignment.appUser
        WHERE assignment.appUser.userId = :userId
          AND assignment.status = :assignmentStatus
          AND assignment.appUser.status = :userStatus
          AND assignment.appUser.accountKind = :accountKind
        """)
    Optional<PlatformAdminAssignment> findActivePrivilegedAssignment(
        @Param("userId") Long userId,
        @Param("assignmentStatus") PlatformAdminAssignmentStatus assignmentStatus,
        @Param("userStatus") AppUserStatus userStatus,
        @Param("accountKind") AppUserAccountKind accountKind
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT assignment
        FROM PlatformAdminAssignment assignment
        JOIN FETCH assignment.appUser
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

    @Query("""
        SELECT assignment
        FROM UserRoleAssignment assignment
        JOIN FETCH assignment.appUser
        JOIN FETCH assignment.region
        WHERE assignment.appUser.userId = :userId
          AND assignment.role = :role
          AND assignment.status = :assignmentStatus
          AND assignment.appUser.status = :userStatus
        """)
    Optional<UserRoleAssignment> findActiveRoleAssignment(
        @Param("userId") Long userId,
        @Param("role") UserRole role,
        @Param("assignmentStatus") UserRoleAssignmentStatus assignmentStatus,
        @Param("userStatus") AppUserStatus userStatus
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT assignment
        FROM UserRoleAssignment assignment
        JOIN FETCH assignment.appUser
        JOIN FETCH assignment.region
        WHERE assignment.appUser.userId = :userId
          AND assignment.role = :role
          AND assignment.status = :assignmentStatus
          AND assignment.appUser.status = :userStatus
        """)
    Optional<UserRoleAssignment> findActiveRoleAssignmentForUpdate(
        @Param("userId") Long userId,
        @Param("role") UserRole role,
        @Param("assignmentStatus") UserRoleAssignmentStatus assignmentStatus,
        @Param("userStatus") AppUserStatus userStatus
    );

    @Query("""
        SELECT new io.regionevent.regioneventbackend.domain.user.repository.PlatformAdminUserListProjection(
            user.userId,
            user.loginIdentifier,
            user.name,
            assignment.role,
            region.regionId,
            region.name,
            user.createdAt
        )
        FROM AppUser user
        LEFT JOIN UserRoleAssignment assignment
            ON assignment.appUser = user
            AND assignment.status = io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignmentStatus.ACTIVE
        LEFT JOIN assignment.region region
        WHERE user.accountKind = io.regionevent.regioneventbackend.domain.user.entity.AppUserAccountKind.ORDINARY
          AND user.status = io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus.ACTIVE
        ORDER BY user.createdAt DESC, user.userId DESC, assignment.role ASC, region.regionId ASC
        """)
    List<PlatformAdminUserListProjection> findPlatformAdminUserList();
}
