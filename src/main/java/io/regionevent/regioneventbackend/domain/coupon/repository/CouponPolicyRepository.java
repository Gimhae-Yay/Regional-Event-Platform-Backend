package io.regionevent.regioneventbackend.domain.coupon.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;

public interface CouponPolicyRepository extends JpaRepository<CouponPolicy, Long> {

    @EntityGraph(attributePaths = {"content", "region"})
    Optional<CouponPolicy> findByCouponPolicyId(Long couponPolicyId);

    @EntityGraph(attributePaths = {"content", "region"})
    @Query("""
        SELECT couponPolicy
        FROM CouponPolicy couponPolicy
        WHERE couponPolicy.content.operator.userId = :operatorUserId
            AND couponPolicy.content.region.regionId = :regionId
        ORDER BY couponPolicy.couponPolicyId DESC
        """)
    List<CouponPolicy> findAllByContentOperatorUserIdAndContentRegionId(
        @Param("operatorUserId") Long operatorUserId,
        @Param("regionId") Long regionId
    );

    @EntityGraph(attributePaths = {"content", "region"})
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT couponPolicy
        FROM CouponPolicy couponPolicy
        WHERE couponPolicy.couponPolicyId = :couponPolicyId
        """)
    Optional<CouponPolicy> findByCouponPolicyIdForUpdate(@Param("couponPolicyId") Long couponPolicyId);
}
