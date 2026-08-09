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
