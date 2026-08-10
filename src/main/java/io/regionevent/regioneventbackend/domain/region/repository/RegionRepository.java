package io.regionevent.regioneventbackend.domain.region.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.regionevent.regioneventbackend.domain.region.entity.Region;

public interface RegionRepository extends JpaRepository<Region, Long> {

    boolean existsByRegionCode(String regionCode);

    Optional<Region> findByRegionIdAndIsPublicTrue(Long regionId);

    @Query("""
        SELECT new io.regionevent.regioneventbackend.domain.region.repository.PlatformAdminRegionListProjection(
            region.regionId,
            region.regionCode,
            region.name,
            region.isPublic,
            COUNT(assignment),
            region.createdAt,
            region.updatedAt
        )
        FROM Region region
        LEFT JOIN UserRoleAssignment assignment
            ON assignment.region = region
            AND assignment.role = io.regionevent.regioneventbackend.domain.user.entity.UserRole.REGION_ADMIN
            AND assignment.status = io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignmentStatus.ACTIVE
            AND assignment.appUser.accountKind = io.regionevent.regioneventbackend.domain.user.entity.AppUserAccountKind.ORDINARY
            AND assignment.appUser.status = io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus.ACTIVE
        WHERE :isPublic IS NULL OR region.isPublic = :isPublic
        GROUP BY
            region.regionId,
            region.regionCode,
            region.name,
            region.isPublic,
            region.createdAt,
            region.updatedAt
        ORDER BY region.name ASC, region.regionId ASC
        """)
    List<PlatformAdminRegionListProjection> findPlatformAdminRegionList(
        @Param("isPublic") Boolean isPublic
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Region> findByRegionId(Long regionId);

    @Query("""
        SELECT new io.regionevent.regioneventbackend.domain.region.repository.PublicRegionVerificationProjection(
            region.regionId
        )
        FROM Region region
        WHERE region.isPublic = true
        ORDER BY region.name ASC, region.regionId ASC
        """)
    List<PublicRegionVerificationProjection> findPublicRegionVerifications();

    @Query("""
        SELECT new io.regionevent.regioneventbackend.domain.region.repository.PublicRegionStaticProjection(
            region.regionId,
            region.regionCode,
            region.name
        )
        FROM Region region
        WHERE region.regionId = :regionId
            AND region.isPublic = true
        """)
    Optional<PublicRegionStaticProjection> findPublicRegionStaticInfo(@Param("regionId") Long regionId);
}
