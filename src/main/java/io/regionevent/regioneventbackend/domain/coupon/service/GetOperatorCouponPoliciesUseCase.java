package io.regionevent.regioneventbackend.domain.coupon.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService.AuthorizedOperator;

@Service
public class GetOperatorCouponPoliciesUseCase {

    private final OperatorAuthorizationService operatorAuthorizationService;
    private final CouponPolicyService couponPolicyService;

    public GetOperatorCouponPoliciesUseCase(
        OperatorAuthorizationService operatorAuthorizationService,
        CouponPolicyService couponPolicyService
    ) {
        this.operatorAuthorizationService = operatorAuthorizationService;
        this.couponPolicyService = couponPolicyService;
    }

    @Transactional(readOnly = true)
    public List<OperatorCouponPolicySummary> findAll(Long userId) {
        AuthorizedOperator operator = operatorAuthorizationService.requireAuthorizedOperator(userId);
        return couponPolicyService.findAllByContentOperatorUserIdAndContentRegionId(
                operator.user().getUserId(),
                operator.region().getRegionId()
            ).stream()
            .map(OperatorCouponPolicySummary::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public OperatorCouponPolicyDetail find(
        Long userId,
        Long couponPolicyId
    ) {
        CouponPolicy couponPolicy = couponPolicyService.find(couponPolicyId);
        operatorAuthorizationService.authorizeOwnedContent(
            userId,
            couponPolicy.getContent().getOperator(),
            couponPolicy.getContent().getRegion()
        );
        return OperatorCouponPolicyDetail.from(couponPolicy);
    }
}
