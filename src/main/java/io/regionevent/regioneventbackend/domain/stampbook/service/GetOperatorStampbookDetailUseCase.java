package io.regionevent.regioneventbackend.domain.stampbook.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
import io.regionevent.regioneventbackend.domain.stampbook.dto.OperatorStampbookDetailResponse;
import io.regionevent.regioneventbackend.domain.stampbook.entity.Stampbook;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookContent;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService.AuthorizedOperator;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class GetOperatorStampbookDetailUseCase {

    private final OperatorAuthorizationService operatorAuthorizationService;
    private final StampbookService stampbookService;
    private final StampbookContentService stampbookContentService;

    public GetOperatorStampbookDetailUseCase(
        OperatorAuthorizationService operatorAuthorizationService,
        StampbookService stampbookService,
        StampbookContentService stampbookContentService
    ) {
        this.operatorAuthorizationService = operatorAuthorizationService;
        this.stampbookService = stampbookService;
        this.stampbookContentService = stampbookContentService;
    }

    @Transactional(readOnly = true)
    public OperatorStampbookDetailResponse get(
        Long userId,
        Long stampbookId
    ) {
        AuthorizedOperator operator = operatorAuthorizationService.requireAuthorizedOperator(userId);
        Stampbook stampbook = stampbookService.findOperatorDetail(stampbookId);
        validateRegionScope(operator, stampbook);

        List<StampbookContent> targetContents = stampbookContentService.findDetails(stampbookId);
        validateDetailIntegrity(stampbook, targetContents);
        validateTargetContentOwnership(operator, targetContents);
        return OperatorStampbookDetailResponse.from(stampbook, targetContents);
    }

    private void validateRegionScope(
        AuthorizedOperator operator,
        Stampbook stampbook
    ) {
        if (!operator.region().getRegionId().equals(stampbook.getRegion().getRegionId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private void validateDetailIntegrity(
        Stampbook stampbook,
        List<StampbookContent> targetContents
    ) {
        CouponPolicy rewardCouponPolicy = stampbook.getRewardCouponPolicy();
        Long stampbookRegionId = stampbook.getRegion().getRegionId();
        if (targetContents.isEmpty()
            || rewardCouponPolicy.getCouponPolicyId() == null
            || rewardCouponPolicy.getRegion() == null
            || !stampbookRegionId.equals(rewardCouponPolicy.getRegion().getRegionId())
            || rewardCouponPolicy.getIssuanceType() != CouponIssuanceType.STAMPBOOK_COMPLETION
            || targetContents.stream().map(StampbookContent::getContent).anyMatch(content -> content == null
                || content.getContentId() == null
                || content.getRegion() == null
                || !stampbookRegionId.equals(content.getRegion().getRegionId()))) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private void validateTargetContentOwnership(
        AuthorizedOperator operator,
        List<StampbookContent> targetContents
    ) {
        if (targetContents.stream().map(StampbookContent::getContent).anyMatch(content -> !isOwnedBy(
            content,
            operator
        ))) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private boolean isOwnedBy(
        Content content,
        AuthorizedOperator operator
    ) {
        return content.isOwnedBy(operator.user().getUserId())
            && content.isScopedTo(operator.region().getRegionId());
    }
}
