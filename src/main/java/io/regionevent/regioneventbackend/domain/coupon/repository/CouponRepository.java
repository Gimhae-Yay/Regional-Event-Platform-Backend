package io.regionevent.regioneventbackend.domain.coupon.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.regionevent.regioneventbackend.domain.coupon.entity.Coupon;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponStatus;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

    @EntityGraph(attributePaths = {"couponPolicy", "couponPolicy.content", "couponPolicy.region", "user"})
    Optional<Coupon> findByCouponIdAndUserUserId(
        Long couponId,
        Long userId
    );

    @EntityGraph(attributePaths = {"couponPolicy", "couponPolicy.content", "couponPolicy.region", "user"})
    List<Coupon> findAllByUserUserIdOrderByIssuedAtDescCouponIdDesc(Long userId);

    @EntityGraph(attributePaths = {"couponPolicy", "couponPolicy.content", "couponPolicy.region", "user"})
    List<Coupon> findAllByUserUserIdAndStatusOrderByIssuedAtDescCouponIdDesc(
        Long userId,
        CouponStatus status
    );

    @EntityGraph(attributePaths = {"couponPolicy", "couponPolicy.content", "couponPolicy.region", "user"})
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT coupon
        FROM Coupon coupon
        WHERE coupon.couponId = :couponId
        """)
    Optional<Coupon> findByCouponIdForUpdate(@Param("couponId") Long couponId);

    @Query(value = """
        SELECT *
        FROM coupon
        WHERE coupon_id = :couponId
            AND status = 'AVAILABLE'
            AND expires_at > CURRENT_TIMESTAMP(6)
        FOR UPDATE
        """, nativeQuery = true)
    Optional<Coupon> findAvailableByCouponIdForUpdate(@Param("couponId") Long couponId);

    @Query(value = "SELECT UNIX_TIMESTAMP(CURRENT_TIMESTAMP(6))", nativeQuery = true)
    BigDecimal findCurrentEpochSeconds();
}
