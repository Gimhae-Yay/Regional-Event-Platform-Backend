package io.regionevent.regioneventbackend.domain.region.repository;

import java.util.Optional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import org.springframework.stereotype.Repository;

import io.regionevent.regioneventbackend.domain.region.entity.Region;

@Repository
class RegionRepositoryImpl implements RegionRepositoryCustom {

    private final EntityManager entityManager;

    RegionRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Optional<Region> findByRegionIdForUpdate(Long regionId) {
        Region region = entityManager.find(Region.class, regionId);
        if (region == null) {
            return Optional.empty();
        }

        entityManager.detach(region);
        return Optional.ofNullable(entityManager.find(Region.class, regionId, LockModeType.PESSIMISTIC_WRITE));
    }
}
