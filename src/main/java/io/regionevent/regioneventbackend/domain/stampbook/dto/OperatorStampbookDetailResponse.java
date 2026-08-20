package io.regionevent.regioneventbackend.domain.stampbook.dto;

import java.time.Instant;
import java.util.List;

import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicyStatus;
import io.regionevent.regioneventbackend.domain.stampbook.entity.Stampbook;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookContent;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookStatus;

public record OperatorStampbookDetailResponse(
    String stampbookId,
    String title,
    String regionId,
    StampbookStatus status,
    List<TargetContentResponse> targetContents,
    RewardCouponPolicyResponse rewardCouponPolicy,
    Instant publishedAt,
    Instant endedAt
) {

    public static OperatorStampbookDetailResponse from(
        Stampbook stampbook,
        List<StampbookContent> targetContents
    ) {
        return new OperatorStampbookDetailResponse(
            stampbook.getStampbookId().toString(),
            stampbook.getTitle(),
            stampbook.getRegion().getRegionId().toString(),
            stampbook.getStatus(),
            targetContents.stream().map(TargetContentResponse::from).toList(),
            RewardCouponPolicyResponse.from(stampbook),
            stampbook.getPublishedAt(),
            stampbook.getEndedAt()
        );
    }

    public record TargetContentResponse(
        String contentId,
        String regionId,
        String title,
        ContentStatus status
    ) {

        private static TargetContentResponse from(StampbookContent stampbookContent) {
            return new TargetContentResponse(
                stampbookContent.getContent().getContentId().toString(),
                stampbookContent.getContent().getRegion().getRegionId().toString(),
                stampbookContent.getContent().getTitle(),
                stampbookContent.getContent().getStatus()
            );
        }
    }

    public record RewardCouponPolicyResponse(
        String couponPolicyId,
        String regionId,
        CouponIssuanceType issuanceType,
        CouponPolicyStatus status
    ) {

        private static RewardCouponPolicyResponse from(Stampbook stampbook) {
            return new RewardCouponPolicyResponse(
                stampbook.getRewardCouponPolicy().getCouponPolicyId().toString(),
                stampbook.getRewardCouponPolicy().getRegion().getRegionId().toString(),
                stampbook.getRewardCouponPolicy().getIssuanceType(),
                stampbook.getRewardCouponPolicy().getStatus()
            );
        }
    }
}
