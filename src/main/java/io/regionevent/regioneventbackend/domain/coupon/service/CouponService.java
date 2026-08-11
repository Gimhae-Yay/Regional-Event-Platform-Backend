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

    @Transactional(propagation = Propagation.MANDATORY)
    public List<Long> findExpirationCandidateIds(int batchSize) {
        return couponRepository.findExpirationCandidateIds(batchSize);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Coupon expireIfAvailableAndExpired(Long couponId) {
        if (couponRepository.expireIfAvailableAndExpired(couponId) == 0) {
            return null;
        }
        return couponRepository.findExpirationTargetByCouponId(couponId).orElseThrow();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Instant findCurrentTimestamp() {
        BigDecimal currentEpochSeconds = couponRepository.findCurrentEpochSeconds();
        long epochSecond = currentEpochSeconds.longValue();
        long nanoAdjustment = currentEpochSeconds
            .remainder(BigDecimal.ONE)
            .movePointRight(9)
            .longValue();
        return Instant.ofEpochSecond(epochSecond, nanoAdjustment);
    }
}
