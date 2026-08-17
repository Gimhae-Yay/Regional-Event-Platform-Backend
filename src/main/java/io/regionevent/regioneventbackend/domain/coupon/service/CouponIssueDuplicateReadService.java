package io.regionevent.regioneventbackend.domain.coupon.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.coupon.repository.CouponIssuanceRepository;

@Service
public class CouponIssueDuplicateReadService {

    private final CouponIssuanceRepository couponIssuanceRepository;

    public CouponIssueDuplicateReadService(CouponIssuanceRepository couponIssuanceRepository) {
        this.couponIssuanceRepository = couponIssuanceRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<CouponIssueResult> find(String issuanceIdentityHash) {
        return couponIssuanceRepository.findByIssuanceIdentityHash(issuanceIdentityHash)
            .map(issuance -> CouponIssueResult.from(issuance.getCoupon(), true));
    }
}
