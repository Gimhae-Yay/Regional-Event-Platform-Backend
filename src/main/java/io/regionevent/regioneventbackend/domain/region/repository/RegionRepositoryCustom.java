package io.regionevent.regioneventbackend.domain.region.repository;

import java.util.Optional;

import io.regionevent.regioneventbackend.domain.region.entity.Region;

public interface RegionRepositoryCustom {

    Optional<Region> findByRegionIdForUpdate(Long regionId);
}
