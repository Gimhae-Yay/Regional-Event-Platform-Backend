package io.regionevent.regioneventbackend.domain.coupon.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.coupon.entity.CouponStatusHistory;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponStatus;
import io.regionevent.regioneventbackend.domain.coupon.repository.CouponStatusHistoryRepository;

import java.util.Optional;

@Service
public class CouponStatusHistoryService {

    private final CouponStatusHistoryRepository couponStatusHistoryRepository;

    public CouponStatusHistoryService(CouponStatusHistoryRepository couponStatusHistoryRepository) {
        this.couponStatusHistoryRepository = couponStatusHistoryRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public CouponStatusHistory create(CouponStatusHistory couponStatusHistory) {
        return couponStatusHistoryRepository.saveAndFlush(couponStatusHistory);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<CouponStatusHistory> findMissionRewardInitialByCouponId(Long couponId) {
        return couponStatusHistoryRepository.findFirstByCouponCouponIdOrderByOccurredAtAsc(couponId)
            .filter(history -> history.getPreviousStatus() == null)
            .filter(history -> history.getNextStatus() == CouponStatus.AVAILABLE)
            .filter(history -> "MISSION_REWARD_ISSUED".equals(history.getReasonCode()))
            .filter(history -> "USER".equals(history.getActorKind()));
    }
}
