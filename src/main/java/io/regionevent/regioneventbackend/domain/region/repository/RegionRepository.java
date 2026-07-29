package io.regionevent.regioneventbackend.domain.region.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import io.regionevent.regioneventbackend.domain.region.entity.Region;

public interface RegionRepository extends JpaRepository<Region, Long> {
}
