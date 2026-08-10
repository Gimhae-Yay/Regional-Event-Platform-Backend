package io.regionevent.regioneventbackend.domain.coupon.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicyUpdateHistory;
import io.regionevent.regioneventbackend.domain.coupon.repository.CouponPolicyUpdateHistoryRepository;

@Service
public class CouponPolicyUpdateHistoryService {

    private final CouponPolicyUpdateHistoryRepository couponPolicyUpdateHistoryRepository;

    public CouponPolicyUpdateHistoryService(
        CouponPolicyUpdateHistoryRepository couponPolicyUpdateHistoryRepository
    ) {
        this.couponPolicyUpdateHistoryRepository = couponPolicyUpdateHistoryRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public CouponPolicyUpdateHistory create(CouponPolicyUpdateHistory couponPolicyUpdateHistory) {
        return couponPolicyUpdateHistoryRepository.saveAndFlush(couponPolicyUpdateHistory);
    }
}
