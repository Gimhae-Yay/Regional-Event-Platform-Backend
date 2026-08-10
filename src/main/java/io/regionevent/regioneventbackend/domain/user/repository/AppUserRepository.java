package io.regionevent.regioneventbackend.domain.user.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import io.regionevent.regioneventbackend.domain.user.entity.AppUser;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    boolean existsByLoginIdentifier(String loginIdentifier);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AppUser> findByLoginIdentifier(String loginIdentifier);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from AppUser user where user.userId = ?1")
    Optional<AppUser> findByIdForUpdate(Long userId);

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
