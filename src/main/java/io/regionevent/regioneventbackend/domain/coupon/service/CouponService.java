package io.regionevent.regioneventbackend.domain.coupon.service;

<<<<<<< HEAD
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.coupon.entity.Coupon;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponStatus;
=======
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.coupon.entity.Coupon;
>>>>>>> origin/dev
import io.regionevent.regioneventbackend.domain.coupon.repository.CouponRepository;

@Service
public class CouponService {

    private final CouponRepository couponRepository;

    public CouponService(CouponRepository couponRepository) {
        this.couponRepository = couponRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Coupon create(Coupon coupon) {
        return couponRepository.saveAndFlush(coupon);
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
