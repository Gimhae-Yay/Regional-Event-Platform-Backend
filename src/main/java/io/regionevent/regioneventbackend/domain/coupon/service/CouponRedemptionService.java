package io.regionevent.regioneventbackend.domain.coupon.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.coupon.entity.CouponRedemption;
import io.regionevent.regioneventbackend.domain.coupon.repository.CouponRedemptionRepository;

@Service
public class CouponRedemptionService {

    private final CouponRedemptionRepository couponRedemptionRepository;

    public CouponRedemptionService(CouponRedemptionRepository couponRedemptionRepository) {
        this.couponRedemptionRepository = couponRedemptionRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public List<CouponRedemption> findAllByCouponId(Long couponId) {
        return couponRedemptionRepository
            .findAllByCouponCouponIdOrderByRedeemedAtDescCouponRedemptionIdDesc(couponId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public CouponRedemption create(CouponRedemption couponRedemption) {
        return couponRedemptionRepository.saveAndFlush(couponRedemption);
    }
}
