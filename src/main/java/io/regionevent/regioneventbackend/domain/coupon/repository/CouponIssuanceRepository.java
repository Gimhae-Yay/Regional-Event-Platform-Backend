package io.regionevent.regioneventbackend.domain.coupon.repository;

import java.util.Optional;

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

}
