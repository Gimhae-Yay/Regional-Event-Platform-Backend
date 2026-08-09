package io.regionevent.regioneventbackend.domain.coupon.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuance;
import io.regionevent.regioneventbackend.domain.coupon.repository.CouponIssuanceRepository;

@Service
public class CouponIssuanceService {

    private final CouponIssuanceRepository couponIssuanceRepository;

    public CouponIssuanceService(CouponIssuanceRepository couponIssuanceRepository) {
        this.couponIssuanceRepository = couponIssuanceRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<CouponIssuance> findByIdentityHash(String issuanceIdentityHash) {
        return couponIssuanceRepository.findByIssuanceIdentityHash(issuanceIdentityHash);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public CouponIssuance create(CouponIssuance couponIssuance) {
        return couponIssuanceRepository.saveAndFlush(couponIssuance);
    }
}
