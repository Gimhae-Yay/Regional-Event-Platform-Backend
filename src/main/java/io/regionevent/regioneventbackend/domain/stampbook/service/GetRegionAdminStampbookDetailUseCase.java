package io.regionevent.regioneventbackend.domain.stampbook.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.service.StampbookReviewRequestReadService;
import io.regionevent.regioneventbackend.domain.audit.service.StampbookReviewRequestReadService.StampbookReviewRequest;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService;

@Service
public class GetRegionAdminStampbookDetailUseCase {

    private final RegionAdminAuthorizationService regionAdminAuthorizationService;
    private final StampbookReviewReadService stampbookReviewReadService;
    private final StampbookReviewRequestReadService stampbookReviewRequestReadService;

    public GetRegionAdminStampbookDetailUseCase(
        RegionAdminAuthorizationService regionAdminAuthorizationService,
        StampbookReviewReadService stampbookReviewReadService,
        StampbookReviewRequestReadService stampbookReviewRequestReadService
    ) {
        this.regionAdminAuthorizationService = regionAdminAuthorizationService;
        this.stampbookReviewReadService = stampbookReviewReadService;
        this.stampbookReviewRequestReadService = stampbookReviewRequestReadService;
    }

    @Transactional(readOnly = true)
    public RegionAdminStampbookDetailResult find(
        Long userId,
        Long stampbookId
    ) {
        Long regionId = regionAdminAuthorizationService.requireAuthorizedRegionId(userId);
        StampbookReviewDetail stampbookDetail = stampbookReviewReadService.findPendingReviewDetail(
            stampbookId,
            regionId
        );
        StampbookReviewRequest reviewRequest = stampbookReviewRequestReadService.findLatest(
            stampbookId,
            regionId
        );

        return new RegionAdminStampbookDetailResult(
            stampbookDetail.stampbookId(),
            stampbookDetail.regionId(),
            stampbookDetail.status(),
            stampbookDetail.targetContents().stream()
                .map(targetContent -> new RegionAdminStampbookDetailResult.TargetContent(
                    targetContent.contentId(),
                    targetContent.regionId(),
                    targetContent.title(),
                    targetContent.status()
                ))
                .toList(),
            new RegionAdminStampbookDetailResult.RewardCouponPolicy(
                stampbookDetail.rewardCouponPolicy().couponPolicyId(),
                stampbookDetail.rewardCouponPolicy().regionId(),
                stampbookDetail.rewardCouponPolicy().issuanceType(),
                stampbookDetail.rewardCouponPolicy().status()
            ),
            reviewRequest.requestedAt(),
            reviewRequest.requestReason()
        );
    }
}
