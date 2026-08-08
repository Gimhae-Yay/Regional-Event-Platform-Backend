package io.regionevent.regioneventbackend.domain.reservation.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationPriceSnapshot;

public interface ReservationPriceSnapshotRepository extends JpaRepository<ReservationPriceSnapshot, Long> {

    @EntityGraph(attributePaths = {"capacityHold", "coupon", "coupon.couponPolicy"})
    Optional<ReservationPriceSnapshot> findByCapacityHoldHoldId(Long holdId);

    @EntityGraph(attributePaths = {"capacityHold", "coupon", "coupon.couponPolicy"})
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT snapshot
        FROM ReservationPriceSnapshot snapshot
        WHERE snapshot.capacityHold.holdId = :holdId
        """)
    Optional<ReservationPriceSnapshot> findByHoldIdForUpdate(@Param("holdId") Long holdId);
}
