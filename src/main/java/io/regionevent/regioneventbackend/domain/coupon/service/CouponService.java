package io.regionevent.regioneventbackend.domain.coupon.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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

    @Transactional(propagation = Propagation.MANDATORY)
    public Coupon create(Coupon coupon) {
        return couponRepository.saveAndFlush(coupon);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Coupon> findByCouponIdForUpdate(Long couponId) {
        return couponRepository.findByCouponIdForUpdate(couponId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean reserveIfAvailableAndNotExpired(Coupon coupon) {
        if (couponRepository.reserveIfAvailableAndNotExpired(coupon.getCouponId()) == 0) {
            return false;
        }
        coupon.reserve();
        return true;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Instant findCurrentDatabaseTime() {
        BigDecimal epochSeconds = couponRepository.findCurrentEpochSeconds();
        long seconds = epochSeconds.longValue();
        long nanos = epochSeconds.remainder(BigDecimal.ONE)
            .movePointRight(9)
            .longValue();
        return Instant.ofEpochSecond(seconds, nanos);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public CouponStatus releaseReservedCoupon(Coupon coupon, Instant releasedAt) {
        return coupon.release(releasedAt);
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
