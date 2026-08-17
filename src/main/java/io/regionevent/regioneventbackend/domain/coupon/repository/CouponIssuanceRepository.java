package io.regionevent.regioneventbackend.domain.coupon.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @EntityGraph(attributePaths = {"coupon", "couponPolicy", "recipientUser", "missionRewardClaim"})
    Optional<CouponIssuance> findByMissionRewardClaimMissionRewardClaimId(Long missionRewardClaimId);

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

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        UPDATE CouponIssuance issuance
        SET issuance.recipientUser = NULL
        WHERE issuance.recipientUser.userId = :userId
        """)
    int unlinkRecipientUserByUserId(@Param("userId") Long userId);

}
