package io.regionevent.regioneventbackend.domain.coupon.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT issuance
        FROM CouponIssuance issuance
        WHERE issuance.issuanceIdentityHash = :issuanceIdentityHash
        """)
    Optional<CouponIssuance> findByIssuanceIdentityHashForUpdate(
        @Param("issuanceIdentityHash") String issuanceIdentityHash
    );
}
