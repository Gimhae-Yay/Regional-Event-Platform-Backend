package io.regionevent.regioneventbackend.domain.coupon.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
import io.regionevent.regioneventbackend.domain.coupon.repository.CouponPolicyRepository;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class CouponPolicyService {

    private final CouponPolicyRepository couponPolicyRepository;

    public CouponPolicyService(CouponPolicyRepository couponPolicyRepository) {
        this.couponPolicyRepository = couponPolicyRepository;
    }

    @Transactional(readOnly = true)
    public CouponPolicy findStampbookRewardPolicy(
        Long couponPolicyId,
        Long regionId
    ) {
        validateRequiredId(couponPolicyId);
        validateRequiredId(regionId);

        CouponPolicy couponPolicy = couponPolicyRepository.findByCouponPolicyId(couponPolicyId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!couponPolicy.getRegion().getRegionId().equals(regionId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (couponPolicy.getIssuanceType() != CouponIssuanceType.STAMPBOOK_COMPLETION) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return couponPolicy;
    }

    private void validateRequiredId(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }
}
