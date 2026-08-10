package io.regionevent.regioneventbackend.domain.user.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import io.regionevent.regioneventbackend.domain.user.entity.AppUserAccountKind;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminAssignment;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminAssignmentStatus;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminGrade;

public interface PlatformAdminAssignmentRepository extends JpaRepository<PlatformAdminAssignment, Long> {

    @EntityGraph(attributePaths = "appUser")
    Optional<PlatformAdminAssignment> findByAppUserUserIdAndStatusAndAppUserStatusAndAppUserAccountKind(
        Long userId,
        PlatformAdminAssignmentStatus status,
        AppUserStatus appUserStatus,
        AppUserAccountKind appUserAccountKind
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
