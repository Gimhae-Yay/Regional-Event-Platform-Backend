package io.regionevent.regioneventbackend.domain.stampbook.service;

import java.util.List;

import org.springframework.stereotype.Service;

import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
import io.regionevent.regioneventbackend.domain.stampbook.entity.Stampbook;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookStatus;
import io.regionevent.regioneventbackend.domain.stampbook.repository.StampbookRepository;
import io.regionevent.regioneventbackend.domain.stampbook.repository.StampbookReviewTargetContentProjection;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class StampbookReviewReadService {

    private final StampbookRepository stampbookRepository;

    public StampbookReviewReadService(StampbookRepository stampbookRepository) {
        this.stampbookRepository = stampbookRepository;
    }

    public StampbookReviewDetail findPendingReviewDetail(
        Long stampbookId,
        Long regionId
    ) {
        validatePositiveId(stampbookId, "stampbookId");
        validatePositiveId(regionId, "regionId");

        Stampbook stampbook = stampbookRepository.findReviewDetailByStampbookIdAndRegionIdAndStatus(
                stampbookId,
                regionId,
                StampbookStatus.PENDING_REVIEW
            )
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        List<StampbookReviewTargetContentProjection> targetContents = stampbookRepository
            .findReviewTargetContentsByStampbookId(stampbookId);
        validateDetailIntegrity(stampbook, targetContents);

        return new StampbookReviewDetail(
            stampbook.getStampbookId(),
            stampbook.getRegion().getRegionId(),
            stampbook.getStatus(),
            targetContents.stream()
                .map(targetContent -> new StampbookReviewDetail.TargetContent(
                    targetContent.contentId(),
                    targetContent.regionId(),
                    targetContent.title(),
                    targetContent.status()
                ))
                .toList(),
            new StampbookReviewDetail.RewardCouponPolicy(
                stampbook.getRewardCouponPolicy().getCouponPolicyId(),
                stampbook.getRewardCouponPolicy().getRegion().getRegionId(),
                stampbook.getRewardCouponPolicy().getIssuanceType(),
                stampbook.getRewardCouponPolicy().getStatus()
            )
        );
    }

    private void validateDetailIntegrity(
        Stampbook stampbook,
        List<StampbookReviewTargetContentProjection> targetContents
    ) {
        CouponPolicy rewardCouponPolicy = stampbook.getRewardCouponPolicy();
        Long stampbookRegionId = stampbook.getRegion().getRegionId();
        if (targetContents.isEmpty()
            || rewardCouponPolicy.getCouponPolicyId() == null
            || rewardCouponPolicy.getRegion() == null
            || !stampbookRegionId.equals(rewardCouponPolicy.getRegion().getRegionId())
            || rewardCouponPolicy.getIssuanceType() != CouponIssuanceType.STAMPBOOK_COMPLETION
            || targetContents.stream().anyMatch(targetContent -> !stampbookRegionId.equals(
                targetContent.regionId()
            ))) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private void validatePositiveId(
        Long value,
        String fieldName
    ) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
    }
}
