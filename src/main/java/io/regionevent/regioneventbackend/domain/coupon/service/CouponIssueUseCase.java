package io.regionevent.regioneventbackend.domain.coupon.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

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
    private static final Pattern POSITIVE_DECIMAL_PATTERN = Pattern.compile("^[1-9][0-9]*$");

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
        } catch (CouponIssueDataIntegrityViolationException exception) {
            return findDuplicateIssue(
                userId,
                exception.couponPolicyId(),
                exception.command(),
                requestId,
                exception.getCause()
            );
        } catch (DataIntegrityViolationException exception) {
            return findDuplicateIssue(userId, couponPolicyId, command, requestId, exception);
        }
    }

    public CouponIssueResult issue(
        Long userId,
        String couponPolicyId,
        String issueSourceType,
        String sourceId,
        UUID requestId
    ) {
        try {
            CouponIssueResult result = transactionTemplate.execute(status -> issueWithinTransaction(
                userId,
                requestId,
                () -> new CouponIssueRequest(
                    toPositiveId(couponPolicyId),
                    new CouponIssueCommand(toIssueSourceType(issueSourceType), toPositiveId(sourceId))
                )
            ));
            log.info(
                "Coupon issue succeeded. requestId={}, couponPolicyId={}, couponId={}, duplicate={}",
                requestId,
                couponPolicyId,
                result.couponId(),
                result.duplicate()
            );
            return result;
        } catch (CouponIssueDataIntegrityViolationException exception) {
            return findDuplicateIssue(
                userId,
                exception.couponPolicyId(),
                exception.command(),
                requestId,
                exception.getCause()
            );
        }
    }

    private CouponIssueResult issueWithinTransaction(
        Long userId,
        Long couponPolicyId,
        CouponIssueCommand command,
        UUID requestId
    ) {
        return issueWithinTransaction(
            userId,
            requestId,
            () -> new CouponIssueRequest(couponPolicyId, command)
        );
    }

    private CouponIssueResult issueWithinTransaction(
        Long userId,
        UUID requestId,
        Supplier<CouponIssueRequest> requestSupplier
    ) {
        AuditEventActor actor = null;
        CouponPolicy couponPolicy = null;
        CouponIssueRequest request = null;

        try {
            AppUser user = appUserService.findActiveUserForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
            actor = new AuditEventActor(user, UserRole.VISITOR);
            request = requestSupplier.get();
            couponPolicy = couponPolicyService.findForIssue(request.couponPolicyId());

            CouponIssueResult existingResult = findExistingVisitIssue(userId, request.couponPolicyId(), request.command());
            if (existingResult != null) {
                return existingResult;
            }
            IssueSource issueSource = findIssueSource(couponPolicy, user, request.command());
            if (request.command().issueSourceType() != CouponIssuanceType.VISIT) {
                existingResult = findExistingIssue(issueSource.identityHash());
                if (existingResult != null) {
                    return existingResult;
                }
            }
            return issueNewCoupon(couponPolicy, user, request.command().issueSourceType(), issueSource, actor, requestId);
        } catch (BusinessException exception) {
            recordFailure(requestId, actor, couponPolicy, exception.getErrorCode());
            log.warn(
                "Coupon issue rejected. requestId={}, couponPolicyId={}, errorCode={}",
                requestId,
                request == null ? null : request.couponPolicyId(),
                exception.getErrorCode().code()
            );
            throw exception;
        } catch (DataIntegrityViolationException exception) {
            if (request == null) {
                throw exception;
            }
            throw new CouponIssueDataIntegrityViolationException(
                request.couponPolicyId(), request.command(), exception
            );
        } catch (RuntimeException exception) {
            recordFailure(requestId, actor, couponPolicy, ErrorCode.INTERNAL_SERVER_ERROR);
            log.error(
                "Coupon issue failed. requestId={}, couponPolicyId={}, errorCode={}",
                requestId,
                request == null ? null : request.couponPolicyId(),
                ErrorCode.INTERNAL_SERVER_ERROR.code(),
                exception
            );
            throw exception;
        }
    }

    private CouponIssueResult findDuplicateIssue(
        Long userId,
        Long couponPolicyId,
        CouponIssueCommand command,
        UUID requestId,
        DataIntegrityViolationException exception
    ) {
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

    private CouponIssueResult findExistingVisitIssue(
        Long userId,
        Long couponPolicyId,
        CouponIssueCommand command
    ) {
        if (command.issueSourceType() != CouponIssuanceType.VISIT) {
            return null;
        }
        return findExistingIssue(identityHash(userId, couponPolicyId, command));
    }

    private CouponIssueResult findExistingIssue(String issuanceIdentityHash) {
        return couponIssuanceService.findByIdentityHashForUpdate(issuanceIdentityHash)
            .map(issuance -> CouponIssueResult.from(issuance.getCoupon(), true))
            .orElse(null);
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

    private Long toPositiveId(String value) {
        if (value == null || !POSITIVE_DECIMAL_PATTERN.matcher(value).matches()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, exception);
        }
    }

    private CouponIssuanceType toIssueSourceType(String value) {
        try {
            CouponIssuanceType issueSourceType = CouponIssuanceType.valueOf(value);
            if (issueSourceType == CouponIssuanceType.MISSION_REWARD) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }
            return issueSourceType;
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, exception);
        }
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

    private record CouponIssueRequest(Long couponPolicyId, CouponIssueCommand command) {
    }

    private static class CouponIssueDataIntegrityViolationException extends RuntimeException {

        private final Long couponPolicyId;
        private final CouponIssueCommand command;

        CouponIssueDataIntegrityViolationException(
            Long couponPolicyId,
            CouponIssueCommand command,
            DataIntegrityViolationException cause
        ) {
            super(cause);
            this.couponPolicyId = couponPolicyId;
            this.command = command;
        }

        Long couponPolicyId() {
            return couponPolicyId;
        }

        CouponIssueCommand command() {
            return command;
        }

        @Override
        public DataIntegrityViolationException getCause() {
            return (DataIntegrityViolationException) super.getCause();
        }
    }

    public record CouponIssueCommand(
        CouponIssuanceType issueSourceType,
        Long sourceId
    ) {
    }
}
