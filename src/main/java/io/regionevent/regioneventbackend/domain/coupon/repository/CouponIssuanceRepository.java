package io.regionevent.regioneventbackend.domain.coupon.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuance;

public interface CouponIssuanceRepository extends JpaRepository<CouponIssuance, Long> {

    @EntityGraph(attributePaths = {
        "coupon",
        "couponPolicy",
        "recipientUser",
        "visit",
        "missionRewardClaim",
        "stampbookRewardGrant"
    })
    Optional<CouponIssuance> findByIssuanceIdentityHash(String issuanceIdentityHash);

    @EntityGraph(attributePaths = {
        "coupon",
        "coupon.user",
        "couponPolicy",
        "couponPolicy.content",
        "couponPolicy.region",
        "visit",
        "missionRewardClaim",
        "stampbookRewardGrant"
    })
    Optional<CouponIssuance> findByCouponCouponId(Long couponId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {
        "coupon",
        "couponPolicy",
        "recipientUser",
        "visit",
        "missionRewardClaim",
        "stampbookRewardGrant"
    })
    @Query("select issuance from CouponIssuance issuance where issuance.issuanceIdentityHash = ?1")
    Optional<CouponIssuance> findByIssuanceIdentityHashForUpdate(String issuanceIdentityHash);

}
