package io.regionevent.regioneventbackend.domain.reservation.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHold;

public interface CapacityHoldRepository extends JpaRepository<CapacityHold, Long> {
}
