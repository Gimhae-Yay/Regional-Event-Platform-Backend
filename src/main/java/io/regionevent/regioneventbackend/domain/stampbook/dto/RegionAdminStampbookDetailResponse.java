package io.regionevent.regioneventbackend.domain.stampbook.dto;

import java.time.Instant;
import java.util.List;

import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicyStatus;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookStatus;
import io.regionevent.regioneventbackend.domain.stampbook.service.RegionAdminStampbookDetailResult;

public record RegionAdminStampbookDetailResponse(
    String stampbookId,
    String regionId,
    StampbookStatus status,
    List<TargetContentResponse> targetContents,
    RewardCouponPolicyResponse rewardCouponPolicy,
    Instant requestedAt,
    String requestReason
) {

    public static RegionAdminStampbookDetailResponse from(RegionAdminStampbookDetailResult result) {
        return new RegionAdminStampbookDetailResponse(
            result.stampbookId().toString(),
            result.regionId().toString(),
            result.status(),
            result.targetContents().stream()
                .map(TargetContentResponse::from)
                .toList(),
            RewardCouponPolicyResponse.from(result.rewardCouponPolicy()),
            result.requestedAt(),
            result.requestReason()
        );
    }

    public record TargetContentResponse(
        String contentId,
        String regionId,
        String title,
        ContentStatus status
    ) {

        private static TargetContentResponse from(
            RegionAdminStampbookDetailResult.TargetContent targetContent
        ) {
            return new TargetContentResponse(
                targetContent.contentId().toString(),
                targetContent.regionId().toString(),
                targetContent.title(),
                targetContent.status()
            );
        }
    }

    public record RewardCouponPolicyResponse(
        String couponPolicyId,
        String regionId,
        CouponIssuanceType issuanceType,
        CouponPolicyStatus status
    ) {

        private static RewardCouponPolicyResponse from(
            RegionAdminStampbookDetailResult.RewardCouponPolicy rewardCouponPolicy
        ) {
            return new RewardCouponPolicyResponse(
                rewardCouponPolicy.couponPolicyId().toString(),
                rewardCouponPolicy.regionId().toString(),
                rewardCouponPolicy.issuanceType(),
                rewardCouponPolicy.status()
            );
        }
    }
}
