package io.regionevent.regioneventbackend.domain.coupon.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.coupon.entity.Coupon;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponStatus;
import io.regionevent.regioneventbackend.domain.coupon.repository.CouponRepository;

@Service
public class CouponService {

    private final CouponRepository couponRepository;

    public CouponService(CouponRepository couponRepository) {
        this.couponRepository = couponRepository;
    }

    @Transactional(readOnly = true)
    public List<Coupon> findAllByUserId(
        Long userId,
        CouponStatus status
    ) {
        if (status == null) {
            return couponRepository.findAllByUserUserIdOrderByIssuedAtDescCouponIdDesc(userId);
        }
        return couponRepository.findAllByUserUserIdAndStatusOrderByIssuedAtDescCouponIdDesc(
            userId,
            status
        );
    }
}
