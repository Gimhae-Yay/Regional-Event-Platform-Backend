package io.regionevent.regioneventbackend.domain.coupon.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import io.regionevent.regioneventbackend.domain.coupon.entity.CouponStatusHistory;

public interface CouponStatusHistoryRepository extends JpaRepository<CouponStatusHistory, Long> {

    List<CouponStatusHistory> findAllByCouponCouponIdOrderByOccurredAtAsc(Long couponId);

    Optional<CouponStatusHistory> findFirstByCouponCouponIdOrderByOccurredAtAsc(Long couponId);
}
