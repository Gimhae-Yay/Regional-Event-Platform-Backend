package io.regionevent.regioneventbackend.domain.coupon.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.coupon.entity.CouponStatusHistory;
import io.regionevent.regioneventbackend.domain.coupon.repository.CouponStatusHistoryRepository;

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
}
