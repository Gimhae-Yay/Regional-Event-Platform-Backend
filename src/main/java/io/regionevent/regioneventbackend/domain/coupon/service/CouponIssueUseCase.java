package io.regionevent.regioneventbackend.domain.coupon.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventActor;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.audit.service.RecordFailedAuditEventUseCase;
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
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.service.AppUserService;
import io.regionevent.regioneventbackend.domain.visit.entity.Visit;
import io.regionevent.regioneventbackend.domain.visit.service.VisitService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class CouponIssueUseCase {

    private static final Logger log = LoggerFactory.getLogger(CouponIssueUseCase.class);

    private final CouponIssueDuplicateReadService couponIssueDuplicateReadService;
    private final AppUserService appUserService;
    private final CouponPolicyService couponPolicyService;
    private final VisitService visitService;
    private final StampbookRewardGrantService stampbookRewardGrantService;
    private final CouponIssuanceService couponIssuanceService;
    private final CouponService couponService;
    private final CouponStatusHistoryService couponStatusHistoryService;
    private final RecordAuditEventUseCase recordAuditEventUseCase;
    private final RecordFailedAuditEventUseCase recordFailedAuditEventUseCase;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public CouponIssueUseCase(
        CouponIssueDuplicateReadService couponIssueDuplicateReadService,
        AppUserService appUserService,
        CouponPolicyService couponPolicyService,
        VisitService visitService,
        StampbookRewardGrantService stampbookRewardGrantService,
        CouponIssuanceService couponIssuanceService,
        CouponService couponService,
        CouponStatusHistoryService couponStatusHistoryService,
        RecordAuditEventUseCase recordAuditEventUseCase,
        RecordFailedAuditEventUseCase recordFailedAuditEventUseCase,
        PlatformTransactionManager transactionManager,
        Clock clock
    ) {
        this.couponIssueDuplicateReadService = couponIssueDuplicateReadService;
        this.appUserService = appUserService;
        this.couponPolicyService = couponPolicyService;
        this.visitService = visitService;
        this.stampbookRewardGrantService = stampbookRewardGrantService;
        this.couponIssuanceService = couponIssuanceService;
        this.couponService = couponService;
        this.couponStatusHistoryService = couponStatusHistoryService;
        this.recordAuditEventUseCase = recordAuditEventUseCase;
        this.recordFailedAuditEventUseCase = recordFailedAuditEventUseCase;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public CouponIssueResult issue(
        Long userId,
        Long couponPolicyId,
        CouponIssueCommand command,
        UUID requestId
    ) {
        try {
            CouponIssueResult result = transactionTemplate.execute(
                status -> issueWithinTransaction(userId, couponPolicyId, command, requestId)
            );
            log.info(
                "Coupon issue succeeded. requestId={}, couponPolicyId={}, couponId={}, duplicate={}",
                requestId,
                couponPolicyId,
                result.couponId(),
                result.duplicate()
            );
            return result;
        } catch (DataIntegrityViolationException exception) {
            CouponIssueResult result = couponIssueDuplicateReadService.find(identityHash(userId, couponPolicyId, command))
                .orElseThrow(() -> exception);
            log.info(
                "Coupon issue succeeded. requestId={}, couponPolicyId={}, couponId={}, duplicate=true",
                requestId,
                couponPolicyId,
                result.couponId()
            );
            return result;
        }
    }

    private CouponIssueResult issueWithinTransaction(
        Long userId,
        Long couponPolicyId,
        CouponIssueCommand command,
        UUID requestId
    ) {
        AuditEventActor actor = null;
        CouponPolicy couponPolicy = null;

        try {
            AppUser user = appUserService.findActiveUserForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
            actor = new AuditEventActor(user, UserRole.VISITOR);
            couponPolicy = couponPolicyService.findForIssue(couponPolicyId);
            IssueSource issueSource = findIssueSource(couponPolicy, user, command);

            CouponIssueResult existingResult = couponIssuanceService
                .findByIdentityHashForUpdate(issueSource.identityHash())
                .map(issuance -> CouponIssueResult.from(issuance.getCoupon(), true))
                .orElse(null);
            if (existingResult != null) {
                return existingResult;
            }
            return issueNewCoupon(couponPolicy, user, command.issueSourceType(), issueSource, actor, requestId);
        } catch (BusinessException exception) {
            recordFailure(requestId, actor, couponPolicy, exception.getErrorCode());
            log.warn(
                "Coupon issue rejected. requestId={}, couponPolicyId={}, errorCode={}",
                requestId,
                couponPolicyId,
                exception.getErrorCode().code()
            );
            throw exception;
        } catch (DataIntegrityViolationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            recordFailure(requestId, actor, couponPolicy, ErrorCode.INTERNAL_SERVER_ERROR);
            log.error(
                "Coupon issue failed. requestId={}, couponPolicyId={}, errorCode={}",
                requestId,
                couponPolicyId,
                ErrorCode.INTERNAL_SERVER_ERROR.code(),
                exception
            );
            throw exception;
        }
    }

    private CouponIssueResult issueNewCoupon(
        CouponPolicy couponPolicy,
        AppUser user,
        CouponIssuanceType issueSourceType,
        IssueSource issueSource,
        AuditEventActor actor,
        UUID requestId
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
        recordAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            couponPolicy.getRegion(),
            AuditEventTargetType.COUPON,
            coupon.getCouponId(),
            null,
            CouponStatus.AVAILABLE.name(),
            AuditEventResult.SUCCESS,
            "COUPON_ISSUED",
            issueSource.evidenceReference(),
            actor,
            issuedAt
        ));
        return CouponIssueResult.from(coupon, false);
    }

    private IssueSource findIssueSource(CouponPolicy couponPolicy, AppUser user, CouponIssueCommand command) {
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
        if (!sameId(visit.getUser() == null ? null : visit.getUser().getUserId(), user.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (!sameId(visit.getContent().getContentId(), couponPolicy.getContent().getContentId())
            || !sameId(visit.getRegion().getRegionId(), couponPolicy.getRegion().getRegionId())) {
            throw new BusinessException(ErrorCode.COUPON_ISSUE_CONFLICT);
        }
        return new IssueSource(
            CouponIssuanceHasher.hashVisitIssue(couponPolicy.getCouponPolicyId(), user.getUserId()),
            visit,
            null,
            "VISIT:" + visit.getVisitId()
        );
    }

    private IssueSource findStampbookCompletionIssueSource(
        CouponPolicy couponPolicy,
        AppUser user,
        Long stampbookRewardGrantId
    ) {
        StampbookRewardGrant grant = stampbookRewardGrantService.findForCouponIssue(stampbookRewardGrantId);
        if (!sameId(
                grant.getStampbookProgress().getUser() == null
                    ? null
                    : grant.getStampbookProgress().getUser().getUserId(),
                user.getUserId()
            )) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (grant.getStampbookProgress().getStatus() != StampbookProgressStatus.COMPLETED
            || !sameId(grant.getCouponPolicy().getCouponPolicyId(), couponPolicy.getCouponPolicyId())
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
            grant,
            "STAMPBOOK_REWARD_GRANT:" + grant.getStampbookRewardGrantId()
        );
    }

    private void recordFailure(
        UUID requestId,
        AuditEventActor actor,
        CouponPolicy couponPolicy,
        ErrorCode errorCode
    ) {
        recordFailedAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            couponPolicy == null ? null : couponPolicy.getRegion(),
            AuditEventTargetType.COUPON,
            null,
            couponPolicy == null ? null : couponPolicy.getStatus().name(),
            null,
            AuditEventResult.FAILURE,
            errorCode.code(),
            actor,
            clock.instant()
        ));
    }

    private String identityHash(Long userId, Long couponPolicyId, CouponIssueCommand command) {
        return switch (command.issueSourceType()) {
            case VISIT -> CouponIssuanceHasher.hashVisitIssue(couponPolicyId, userId);
            case STAMPBOOK_COMPLETION -> CouponIssuanceHasher.hashStampbookCompletionIssue(
                couponPolicyId,
                command.sourceId()
            );
            case MISSION_REWARD -> throw new BusinessException(ErrorCode.INVALID_INPUT);
        };
    }

    private boolean sameId(Long first, Long second) {
        return first != null && first.equals(second);
    }

    private record IssueSource(
        String identityHash,
        Visit visit,
        StampbookRewardGrant stampbookRewardGrant,
        String evidenceReference
    ) {
    }

    public record CouponIssueCommand(
        CouponIssuanceType issueSourceType,
        Long sourceId
    ) {
    }
}
