package io.regionevent.regioneventbackend.domain.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import io.regionevent.regioneventbackend.domain.user.entity.AppUserAccountKind;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminAssignment;
import io.regionevent.regioneventbackend.domain.user.entity.PlatformAdminAssignmentStatus;

public interface PlatformAdminAssignmentRepository extends JpaRepository<PlatformAdminAssignment, Long> {

    @EntityGraph(attributePaths = "appUser")
    Optional<PlatformAdminAssignment> findByAppUserUserIdAndStatusAndAppUserStatusAndAppUserAccountKind(
        Long userId,
        PlatformAdminAssignmentStatus status,
        AppUserStatus appUserStatus,
        AppUserAccountKind appUserAccountKind
    );
}
