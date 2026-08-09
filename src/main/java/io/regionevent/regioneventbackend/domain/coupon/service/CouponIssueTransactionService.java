package io.regionevent.regioneventbackend.domain.coupon.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.coupon.entity.Coupon;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuance;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponStatus;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponStatusHistory;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookProgressStatus;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookRewardGrant;
import io.regionevent.regioneventbackend.domain.stampbook.service.StampbookRewardGrantService;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.service.AppUserService;
import io.regionevent.regioneventbackend.domain.visit.entity.Visit;
import io.regionevent.regioneventbackend.domain.visit.service.VisitService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class CouponIssueTransactionService {

    private final AppUserService appUserService;
    private final CouponPolicyService couponPolicyService;
    private final VisitService visitService;
    private final StampbookRewardGrantService stampbookRewardGrantService;
    private final CouponIssuanceService couponIssuanceService;
    private final CouponService couponService;
    private final CouponStatusHistoryService couponStatusHistoryService;
    private final Clock clock;

    public CouponIssueTransactionService(
        AppUserService appUserService,
        CouponPolicyService couponPolicyService,
        VisitService visitService,
        StampbookRewardGrantService stampbookRewardGrantService,
        CouponIssuanceService couponIssuanceService,
        CouponService couponService,
        CouponStatusHistoryService couponStatusHistoryService,
        Clock clock
    ) {
        this.appUserService = appUserService;
        this.couponPolicyService = couponPolicyService;
        this.visitService = visitService;
        this.stampbookRewardGrantService = stampbookRewardGrantService;
        this.couponIssuanceService = couponIssuanceService;
        this.couponService = couponService;
        this.couponStatusHistoryService = couponStatusHistoryService;
        this.clock = clock;
    }

    @Transactional
    public CouponIssueResult issue(
        Long userId,
        Long couponPolicyId,
        CouponIssueUseCase.CouponIssueCommand command
    ) {
        AppUser user = appUserService.findActiveUser(userId);
        CouponPolicy couponPolicy = couponPolicyService.findForIssue(couponPolicyId);
        IssueSource issueSource = findIssueSource(couponPolicy, user, command);

        return couponIssuanceService.findByIdentityHash(issueSource.identityHash())
            .map(issuance -> CouponIssueResult.from(issuance.getCoupon(), true))
            .orElseGet(() -> issueNewCoupon(couponPolicy, user, command.issueSourceType(), issueSource));
    }

    private CouponIssueResult issueNewCoupon(
        CouponPolicy couponPolicy,
        AppUser user,
        CouponIssuanceType issueSourceType,
        IssueSource issueSource
    ) {
        Instant issuedAt = clock.instant();
        couponPolicyService.issue(couponPolicy, issueSourceType, issuedAt);
        Coupon coupon = couponService.create(new Coupon(
            couponPolicy, user, issuedAt, issuedAt.plus(couponPolicy.getValidDays(), ChronoUnit.DAYS)
        ));
        couponIssuanceService.create(new CouponIssuance(
            coupon, couponPolicy, user, issueSource.visit(), null, issueSource.stampbookRewardGrant(),
            issueSource.identityHash(), issuedAt
        ));
        couponStatusHistoryService.create(new CouponStatusHistory(
            coupon, null, CouponStatus.AVAILABLE, "COUPON_ISSUED", "USER", issuedAt
        ));
        return CouponIssueResult.from(coupon, false);
    }

    private IssueSource findIssueSource(
        CouponPolicy couponPolicy,
        AppUser user,
        CouponIssueUseCase.CouponIssueCommand command
    ) {
        return switch (command.issueSourceType()) {
            case VISIT -> findVisitIssueSource(couponPolicy, user, command.sourceId());
            case STAMPBOOK_COMPLETION -> findStampbookCompletionIssueSource(
                couponPolicy, user, command.sourceId()
            );
            case MISSION_REWARD -> throw new BusinessException(ErrorCode.INVALID_INPUT);
        };
    }

    private IssueSource findVisitIssueSource(CouponPolicy couponPolicy, AppUser user, Long visitId) {
        Visit visit = visitService.findForCouponIssue(visitId);
        if (!sameId(visit.getUser() == null ? null : visit.getUser().getUserId(), user.getUserId())
            || !sameId(visit.getContent().getContentId(), couponPolicy.getContent().getContentId())
            || !sameId(visit.getRegion().getRegionId(), couponPolicy.getRegion().getRegionId())) {
            throw new BusinessException(ErrorCode.COUPON_ISSUE_CONFLICT);
        }
        return new IssueSource(
            CouponIssuanceHasher.hashVisitIssue(couponPolicy.getCouponPolicyId(), user.getUserId()), visit, null
        );
    }

    private IssueSource findStampbookCompletionIssueSource(
        CouponPolicy couponPolicy,
        AppUser user,
        Long stampbookRewardGrantId
    ) {
        StampbookRewardGrant grant = stampbookRewardGrantService.findForCouponIssue(stampbookRewardGrantId);
        if (grant.getStampbookProgress().getStatus() != StampbookProgressStatus.COMPLETED
            || !sameId(grant.getCouponPolicy().getCouponPolicyId(), couponPolicy.getCouponPolicyId())
            || !sameId(
                grant.getStampbookProgress().getUser() == null
                    ? null
                    : grant.getStampbookProgress().getUser().getUserId(),
                user.getUserId()
            )
            || !sameId(
                grant.getStampbookProgress().getStampbook().getRegion().getRegionId(),
                couponPolicy.getRegion().getRegionId()
            )) {
            throw new BusinessException(ErrorCode.COUPON_ISSUE_CONFLICT);
        }
        return new IssueSource(
            CouponIssuanceHasher.hashStampbookCompletionIssue(
                couponPolicy.getCouponPolicyId(), grant.getStampbookRewardGrantId()
            ),
            null,
            grant
        );
    }

    private boolean sameId(Long first, Long second) {
        return first != null && first.equals(second);
    }

    private record IssueSource(String identityHash, Visit visit, StampbookRewardGrant stampbookRewardGrant) {
    }
}
