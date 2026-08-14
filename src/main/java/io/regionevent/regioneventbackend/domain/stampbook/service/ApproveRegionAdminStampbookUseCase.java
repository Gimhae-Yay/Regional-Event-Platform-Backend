package io.regionevent.regioneventbackend.domain.stampbook.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventActor;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.audit.service.RecordFailedAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.service.ContentService;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponIssuanceType;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicy;
import io.regionevent.regioneventbackend.domain.coupon.entity.CouponPolicyStatus;
import io.regionevent.regioneventbackend.domain.coupon.service.CouponPolicyService;
import io.regionevent.regioneventbackend.domain.stampbook.entity.Stampbook;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService.AuthorizedRegionAdmin;
import io.regionevent.regioneventbackend.domain.user.service.UserRoleAssignmentService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class ApproveRegionAdminStampbookUseCase {

    private final RegionAdminAuthorizationService regionAdminAuthorizationService;
    private final CouponPolicyService couponPolicyService;
    private final StampbookService stampbookService;
    private final StampbookContentService stampbookContentService;
    private final ContentService contentService;
    private final UserRoleAssignmentService userRoleAssignmentService;
    private final RecordAuditEventUseCase recordAuditEventUseCase;
    private final RecordFailedAuditEventUseCase recordFailedAuditEventUseCase;
    private final Clock clock;

    public ApproveRegionAdminStampbookUseCase(
        RegionAdminAuthorizationService regionAdminAuthorizationService,
        CouponPolicyService couponPolicyService,
        StampbookService stampbookService,
        StampbookContentService stampbookContentService,
        ContentService contentService,
        UserRoleAssignmentService userRoleAssignmentService,
        RecordAuditEventUseCase recordAuditEventUseCase,
        RecordFailedAuditEventUseCase recordFailedAuditEventUseCase,
        Clock clock
    ) {
        this.regionAdminAuthorizationService = regionAdminAuthorizationService;
        this.couponPolicyService = couponPolicyService;
        this.stampbookService = stampbookService;
        this.stampbookContentService = stampbookContentService;
        this.contentService = contentService;
        this.userRoleAssignmentService = userRoleAssignmentService;
        this.recordAuditEventUseCase = recordAuditEventUseCase;
        this.recordFailedAuditEventUseCase = recordFailedAuditEventUseCase;
        this.clock = clock;
    }

    @Transactional
    public ApproveRegionAdminStampbookResult approve(
        Long userId,
        ApproveRegionAdminStampbookCommand command,
        UUID requestId
    ) {
        validateCommand(command);
        AuthorizedRegionAdmin regionAdmin = regionAdminAuthorizationService
            .requireAuthorizedRegionAdminForUpdate(userId);
        Stampbook stampbook = stampbookService.findStampbook(command.stampbookId());
        StampbookStatus previousStateForFailure = stampbook.getStatus();

        try {
            validateRegionScope(regionAdmin, stampbook);
            Long initiallyReferencedCouponPolicyId = stampbook.getRewardCouponPolicy()
                .getCouponPolicyId();
            CouponPolicy rewardCouponPolicy = couponPolicyService.findForUpdate(
                initiallyReferencedCouponPolicyId
            );
            stampbook = stampbookService.findForUpdate(command.stampbookId());
            previousStateForFailure = stampbook.getStatus();
            validateLockedRewardCouponPolicyLink(stampbook, initiallyReferencedCouponPolicyId);
            validateRegionScope(regionAdmin, stampbook);

            List<Content> targetContents = lockTargetContents(stampbook);
            validatePublicationConditions(stampbook, rewardCouponPolicy);
            validateTargetContents(stampbook, targetContents);

            Instant publishedAt = stampbookService.findCurrentDatabaseTime();
            Stampbook approvedStampbook = stampbookService.approve(stampbook, publishedAt);
            recordSuccess(requestId, approvedStampbook, regionAdmin, command.reason(), publishedAt);
            return ApproveRegionAdminStampbookResult.from(approvedStampbook);
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

    private void validateCommand(ApproveRegionAdminStampbookCommand command) {
        if (command == null
            || command.stampbookId() == null
            || command.stampbookId() <= 0
            || command.reason() == null
            || command.reason().isBlank()
            || command.reason().length() > 500) {
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
        Long lockedCouponPolicyId
    ) {
        if (!stampbook.getRewardCouponPolicy().getCouponPolicyId().equals(lockedCouponPolicyId)) {
            throw new BusinessException(ErrorCode.STAMPBOOK_STATE_CONFLICT);
        }
    }

    private List<Content> lockTargetContents(Stampbook stampbook) {
        List<Long> contentIds = stampbookContentService.findContentIds(stampbook.getStampbookId());
        if (contentIds.isEmpty()) {
            throw new BusinessException(ErrorCode.STAMPBOOK_STATE_CONFLICT);
        }
        return contentService.findStampbookTargetContentsForUpdate(contentIds);
    }

    private void validatePublicationConditions(
        Stampbook stampbook,
        CouponPolicy rewardCouponPolicy
    ) {
        if (stampbook.getStatus() != StampbookStatus.PENDING_REVIEW
            || !stampbook.getRegion().getRegionId().equals(rewardCouponPolicy.getRegion().getRegionId())
            || rewardCouponPolicy.getIssuanceType() != CouponIssuanceType.STAMPBOOK_COMPLETION
            || rewardCouponPolicy.getStatus() != CouponPolicyStatus.PUBLISHED) {
            throw new BusinessException(ErrorCode.STAMPBOOK_STATE_CONFLICT);
        }
    }

    private void validateTargetContents(
        Stampbook stampbook,
        List<Content> targetContents
    ) {
        for (Content targetContent : targetContents) {
            if (!stampbook.getRegion().getRegionId().equals(targetContent.getRegion().getRegionId())) {
                throw new BusinessException(ErrorCode.STAMPBOOK_STATE_CONFLICT);
            }
            validateTargetContentOperator(stampbook, targetContent);
        }
    }

    private void validateTargetContentOperator(
        Stampbook stampbook,
        Content targetContent
    ) {
        try {
            UserRoleAssignment assignment = userRoleAssignmentService.findActiveOperator(
                targetContent.getOperator().getUserId()
            );
            if (!stampbook.getRegion().getRegionId().equals(assignment.getRegion().getRegionId())) {
                throw new BusinessException(ErrorCode.STAMPBOOK_STATE_CONFLICT);
            }
        } catch (BusinessException exception) {
            throw new BusinessException(ErrorCode.STAMPBOOK_STATE_CONFLICT, exception);
        }
    }

    private void recordSuccess(
        UUID requestId,
        Stampbook stampbook,
        AuthorizedRegionAdmin regionAdmin,
        String reason,
        Instant publishedAt
    ) {
        recordAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            stampbook.getRegion(),
            AuditEventTargetType.STAMPBOOK,
            stampbook.getStampbookId(),
            StampbookStatus.PENDING_REVIEW.name(),
            StampbookStatus.PUBLISHED.name(),
            AuditEventResult.SUCCESS,
            null,
            reason,
            null,
            new AuditEventActor(regionAdmin.roleAssignment()),
            publishedAt
        ));
    }

    private void recordFailure(
        UUID requestId,
        Stampbook stampbook,
        StampbookStatus previousState,
        AuthorizedRegionAdmin regionAdmin,
        ErrorCode errorCode
    ) {
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
            clock.instant().truncatedTo(ChronoUnit.MICROS)
        ));
    }

    public record ApproveRegionAdminStampbookCommand(
        Long stampbookId,
        String reason
    ) {

        public ApproveRegionAdminStampbookCommand {
            reason = reason == null ? null : reason.strip();
        }
    }
}
