package io.regionevent.regioneventbackend.domain.stampbook.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventActor;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.audit.service.RecordFailedAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.content.service.ContentService;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponPolicyService;
import io.regionevent.regioneventbackend.domain.stampbook.entity.Stampbook;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookStatus;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService.AuthorizedRegionAdmin;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class RejectRegionAdminStampbookUseCase {

    private static final Logger log = LoggerFactory.getLogger(RejectRegionAdminStampbookUseCase.class);

    private final RegionAdminAuthorizationService regionAdminAuthorizationService;
    private final CouponPolicyService couponPolicyService;
    private final StampbookService stampbookService;
    private final StampbookContentService stampbookContentService;
    private final ContentService contentService;
    private final RecordAuditEventUseCase recordAuditEventUseCase;
    private final RecordFailedAuditEventUseCase recordFailedAuditEventUseCase;

    public RejectRegionAdminStampbookUseCase(
        RegionAdminAuthorizationService regionAdminAuthorizationService,
        CouponPolicyService couponPolicyService,
        StampbookService stampbookService,
        StampbookContentService stampbookContentService,
        ContentService contentService,
        RecordAuditEventUseCase recordAuditEventUseCase,
        RecordFailedAuditEventUseCase recordFailedAuditEventUseCase
    ) {
        this.regionAdminAuthorizationService = regionAdminAuthorizationService;
        this.couponPolicyService = couponPolicyService;
        this.stampbookService = stampbookService;
        this.stampbookContentService = stampbookContentService;
        this.contentService = contentService;
        this.recordAuditEventUseCase = recordAuditEventUseCase;
        this.recordFailedAuditEventUseCase = recordFailedAuditEventUseCase;
    }

    @Transactional
    public RejectRegionAdminStampbookResult reject(
        Long userId,
        RejectRegionAdminStampbookCommand command,
        UUID requestId
    ) {
        validateCommand(command, requestId);
        AuthorizedRegionAdmin regionAdmin = regionAdminAuthorizationService
            .requireAuthorizedRegionAdminForUpdate(userId);
        Stampbook stampbook = stampbookService.findStampbook(command.stampbookId());
        StampbookStatus previousStateForFailure = stampbook.getStatus();

        try {
            validateRegionScope(regionAdmin, stampbook);
            Long initialRewardCouponPolicyId = stampbook.getRewardCouponPolicy().getCouponPolicyId();
            couponPolicyService.findForUpdate(initialRewardCouponPolicyId);
            stampbook = stampbookService.findForUpdate(command.stampbookId());
            previousStateForFailure = stampbook.getStatus();
            validateLockedRewardCouponPolicyLink(stampbook, initialRewardCouponPolicyId);
            validateRegionScope(regionAdmin, stampbook);
            lockTargetContents(stampbook);

            Instant rejectedAt = stampbookService.findCurrentDatabaseTime();
            Stampbook rejectedStampbook = stampbookService.reject(stampbook);
            recordSuccess(requestId, rejectedStampbook, regionAdmin, command.reason(), rejectedAt);
            return RejectRegionAdminStampbookResult.from(rejectedStampbook, rejectedAt);
        } catch (BusinessException exception) {
            recordFailure(
                requestId,
                stampbook,
                previousStateForFailure,
                regionAdmin,
                exception.getErrorCode()
            );
            throw exception;
        } catch (RuntimeException exception) {
            recordFailure(
                requestId,
                stampbook,
                previousStateForFailure,
                regionAdmin,
                ErrorCode.INTERNAL_SERVER_ERROR
            );
            throw exception;
        }
    }

    private void validateCommand(
        RejectRegionAdminStampbookCommand command,
        UUID requestId
    ) {
        if (command == null
            || command.stampbookId() == null
            || command.stampbookId() <= 0
            || command.reason() == null
            || command.reason().isBlank()
            || command.reason().length() > 500
            || requestId == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private void validateRegionScope(
        AuthorizedRegionAdmin regionAdmin,
        Stampbook stampbook
    ) {
        if (!regionAdmin.region().getRegionId().equals(stampbook.getRegion().getRegionId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private void validateLockedRewardCouponPolicyLink(
        Stampbook stampbook,
        Long initialRewardCouponPolicyId
    ) {
        if (!stampbook.getRewardCouponPolicy().getCouponPolicyId().equals(initialRewardCouponPolicyId)) {
            throw new BusinessException(ErrorCode.STAMPBOOK_STATE_CONFLICT);
        }
    }

    private void lockTargetContents(Stampbook stampbook) {
        List<Long> contentIds = stampbookContentService.findContentIds(stampbook.getStampbookId());
        for (Long contentId : contentIds) {
            contentService.findForUpdate(contentId);
        }
    }

    private void recordSuccess(
        UUID requestId,
        Stampbook stampbook,
        AuthorizedRegionAdmin regionAdmin,
        String reason,
        Instant rejectedAt
    ) {
        recordAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            stampbook.getRegion(),
            AuditEventTargetType.STAMPBOOK,
            stampbook.getStampbookId(),
            StampbookStatus.PENDING_REVIEW.name(),
            StampbookStatus.DRAFT.name(),
            AuditEventResult.SUCCESS,
            null,
            reason,
            null,
            new AuditEventActor(regionAdmin.roleAssignment()),
            rejectedAt
        ));
    }

    private void recordFailure(
        UUID requestId,
        Stampbook stampbook,
        StampbookStatus previousState,
        AuthorizedRegionAdmin regionAdmin,
        ErrorCode errorCode
    ) {
        try {
            recordFailedAuditEventUseCase.record(new AuditEventCommand(
                requestId,
                stampbook.getRegion(),
                AuditEventTargetType.STAMPBOOK,
                stampbook.getStampbookId(),
                previousState.name(),
                null,
                AuditEventResult.FAILURE,
                errorCode.code(),
                new AuditEventActor(regionAdmin.roleAssignment()),
                stampbookService.findCurrentDatabaseTime()
            ));
        } catch (RuntimeException exception) {
            log.error(
                "스탬프북 반려 실패 감사 기록에 실패했습니다. requestId={}, stampbookId={}, originalErrorCode={}, auditWriteResult={}",
                requestId,
                stampbook.getStampbookId(),
                errorCode.code(),
                "FAILURE",
                exception
            );
        }
    }

    public record RejectRegionAdminStampbookCommand(
        Long stampbookId,
        String reason
    ) {

        public RejectRegionAdminStampbookCommand {
            reason = reason == null ? null : reason.strip();
        }
    }
}
