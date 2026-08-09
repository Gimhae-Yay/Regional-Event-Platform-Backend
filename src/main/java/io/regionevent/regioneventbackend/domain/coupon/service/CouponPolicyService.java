package io.regionevent.regioneventbackend.domain.coupon.service;

import java.time.Instant;

import org.springframework.stereotype.Service;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
import io.regionevent.regioneventbackend.domain.coupon.repository.CouponPolicyRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;

@Service
public class CouponPolicyService {

    private final CouponPolicyRepository couponPolicyRepository;

    public CouponPolicyService(CouponPolicyRepository couponPolicyRepository) {
        this.couponPolicyRepository = couponPolicyRepository;
    }

    public CouponPolicy create(CreateCouponPolicyCommand command) {
        CouponPolicy couponPolicy = new CouponPolicy(
            command.content(),
            command.region(),
            command.name(),
            command.description(),
            command.issueSourceType(),
            command.discountAmount(),
            command.minimumPaymentAmount(),
            command.validDaysAfterIssue(),
            command.issueStartsAt(),
            command.issueEndsAt(),
            command.totalIssueLimit()
        );
        return couponPolicyRepository.saveAndFlush(couponPolicy);
    }

    public record CreateCouponPolicyCommand(
        Content content,
        Region region,
        String name,
        String description,
        CouponIssuanceType issueSourceType,
        long discountAmount,
        long minimumPaymentAmount,
        int validDaysAfterIssue,
        Instant issueStartsAt,
        Instant issueEndsAt,
        Long totalIssueLimit
    ) {
    }
}
