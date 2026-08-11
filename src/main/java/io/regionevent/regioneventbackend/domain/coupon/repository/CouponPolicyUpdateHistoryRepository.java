package io.regionevent.regioneventbackend.domain.coupon.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicyUpdateHistory;

public interface CouponPolicyUpdateHistoryRepository extends JpaRepository<CouponPolicyUpdateHistory, Long> {
}
