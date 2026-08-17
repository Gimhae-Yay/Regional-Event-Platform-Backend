package io.regionevent.regioneventbackend.domain.coupon.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.regionevent.regioneventbackend.domain.coupon.entity.CouponRedemption;

public interface CouponRedemptionRepository extends JpaRepository<CouponRedemption, Long> {

    @EntityGraph(attributePaths = {"coupon", "reservationPriceSnapshot", "reservation"})
    Optional<CouponRedemption> findByReservationReservationId(Long reservationId);

    @EntityGraph(attributePaths = {"coupon", "reservationPriceSnapshot", "reservation"})
    List<CouponRedemption> findAllByCouponCouponIdOrderByRedeemedAtDescCouponRedemptionIdDesc(Long couponId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT redemption
        FROM CouponRedemption redemption
        WHERE redemption.reservationPriceSnapshot.reservationPriceSnapshotId = :reservationPriceSnapshotId
        """)
    Optional<CouponRedemption> findByReservationPriceSnapshotIdForUpdate(
        @Param("reservationPriceSnapshotId") Long reservationPriceSnapshotId
    );
}
