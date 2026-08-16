package io.regionevent.regioneventbackend.domain.content.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventActor;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevisionInvalidationReason;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevisionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentWithdrawalRequest;
import io.regionevent.regioneventbackend.domain.content.entity.ContentWithdrawalRequestStatus;
import io.regionevent.regioneventbackend.domain.payment.service.ExpirePendingPaymentForTerminatedHoldUseCase;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.service.RegionService;
import io.regionevent.regioneventbackend.domain.reservation.service.CapacityHoldService;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class ApproveContentWithdrawalUseCase {

    private static final String CONTENT_WITHDRAWN_REASON = "CONTENT_WITHDRAWN";
    private static final String CONTENT_WITHDRAWAL_APPROVED_REASON = "CONTENT_WITHDRAWAL_APPROVED";

    private final ContentWithdrawalRequestService contentWithdrawalRequestService;
    private final ContentService contentService;
    private final RegionService regionService;
    private final ContentRevisionInvalidationService contentRevisionInvalidationService;
    private final ContentSessionService contentSessionService;
    private final ContentLogService contentLogService;
    private final CapacityHoldService capacityHoldService;
    private final ExpirePendingPaymentForTerminatedHoldUseCase expirePendingPaymentForTerminatedHoldUseCase;
    private final RegionAdminAuthorizationService regionAdminAuthorizationService;
    private final RecordAuditEventUseCase recordAuditEventUseCase;
    private final PublicCatalogCacheInvalidator publicCatalogCacheInvalidator;

    public ApproveContentWithdrawalUseCase(
        ContentWithdrawalRequestService contentWithdrawalRequestService,
        ContentService contentService,
        RegionService regionService,
        ContentRevisionInvalidationService contentRevisionInvalidationService,
        ContentSessionService contentSessionService,
        ContentLogService contentLogService,
        CapacityHoldService capacityHoldService,
        ExpirePendingPaymentForTerminatedHoldUseCase expirePendingPaymentForTerminatedHoldUseCase,
        RegionAdminAuthorizationService regionAdminAuthorizationService,
        RecordAuditEventUseCase recordAuditEventUseCase,
        PublicCatalogCacheInvalidator publicCatalogCacheInvalidator
    ) {
        this.contentWithdrawalRequestService = contentWithdrawalRequestService;
        this.contentService = contentService;
        this.regionService = regionService;
        this.contentRevisionInvalidationService = contentRevisionInvalidationService;
        this.contentSessionService = contentSessionService;
        this.contentLogService = contentLogService;
        this.capacityHoldService = capacityHoldService;
        this.expirePendingPaymentForTerminatedHoldUseCase = expirePendingPaymentForTerminatedHoldUseCase;
        this.regionAdminAuthorizationService = regionAdminAuthorizationService;
        this.recordAuditEventUseCase = recordAuditEventUseCase;
        this.publicCatalogCacheInvalidator = publicCatalogCacheInvalidator;
    }

    @Transactional
    public ApproveContentWithdrawalResult approve(
        Long userId,
        Long withdrawalRequestId,
        UUID requestId
    ) {
        RegionAdminAuthorizationService.AuthorizedRegionAdmin regionAdmin =
            regionAdminAuthorizationService.requireAuthorizedRegionAdminForUpdate(userId);
        Long contentId = contentWithdrawalRequestService.findContentId(withdrawalRequestId);
        Long regionId = contentService.findContentRegionId(contentId);
        Region region = regionService.findRegionForUpdate(regionId);
        Content content = contentService.findForUpdate(contentId);
        UserRoleAssignment regionAdminAssignment = regionAdmin.authorize(regionId);
        ContentWithdrawalRequest withdrawalRequest =
            contentWithdrawalRequestService.findReviewTargetForUpdate(withdrawalRequestId);

        if (withdrawalRequest.getStatus() == ContentWithdrawalRequestStatus.APPROVED) {
            return ApproveContentWithdrawalResult.from(withdrawalRequest, content);
        }
        if (withdrawalRequest.getStatus() != ContentWithdrawalRequestStatus.PENDING
            || content.getStatus() != ContentStatus.PUBLISHED
            || content.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.CONTENT_STATE_CONFLICT);
        }

        AuditEventActor actor = new AuditEventActor(regionAdminAssignment);
        Instant approvedAt = contentService.findCurrentDatabaseTime();
        contentWithdrawalRequestService.approve(
            withdrawalRequest,
            actor.getAppUser(),
            approvedAt
        );
        Content withdrawnContent = contentService.withdraw(content, approvedAt);
        invalidateActiveRevision(requestId, region, withdrawnContent, actor, approvedAt);
        contentSessionService.lockSuspendTargetsForUpdate(contentId);
        contentLogService.recordWithdrawn(
            withdrawnContent,
            actor.getAppUser(),
            approvedAt,
            withdrawalRequest.getRequestReason()
        );
        capacityHoldService.invalidateAllActiveHoldsForContent(
            contentId,
            CONTENT_WITHDRAWN_REASON
        ).forEach(capacityHold -> expirePendingPaymentForTerminatedHoldUseCase.expire(
            capacityHold,
            requestId,
            actor
        ));
        recordApprovalAuditEvents(
            requestId,
            region,
            withdrawalRequest,
            withdrawnContent,
            actor,
            approvedAt
        );
        publicCatalogCacheInvalidator.invalidateContentAfterCommit(
            regionId,
            contentId,
            withdrawnContent.getVersionNo()
        );
        return ApproveContentWithdrawalResult.from(withdrawalRequest, withdrawnContent);
    }

    private void invalidateActiveRevision(
        UUID requestId,
        Region region,
        Content content,
        AuditEventActor actor,
        Instant approvedAt
    ) {
        contentRevisionInvalidationService.invalidateActiveRevisionForContent(
            content.getContentId(),
            actor.getAppUser(),
            approvedAt,
            ContentRevisionInvalidationReason.CONTENT_WITHDRAWN
        ).ifPresent(revision -> recordAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            region,
            AuditEventTargetType.CONTENT,
            content.getContentId(),
            ContentRevisionStatus.EDIT_REQUESTED.name(),
            ContentRevisionStatus.EDIT_INVALIDATED.name(),
            AuditEventResult.SUCCESS,
            CONTENT_WITHDRAWN_REASON,
            actor,
            approvedAt
        )));
    }

    private void recordApprovalAuditEvents(
        UUID requestId,
        Region region,
        ContentWithdrawalRequest withdrawalRequest,
        Content content,
        AuditEventActor actor,
        Instant approvedAt
    ) {
        recordAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            region,
            AuditEventTargetType.CONTENT_WITHDRAWAL_REQUEST,
            withdrawalRequest.getContentWithdrawalRequestId(),
            ContentWithdrawalRequestStatus.PENDING.name(),
            ContentWithdrawalRequestStatus.APPROVED.name(),
            AuditEventResult.SUCCESS,
            CONTENT_WITHDRAWAL_APPROVED_REASON,
            actor,
            approvedAt
        ));
        recordAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            region,
            AuditEventTargetType.CONTENT,
            content.getContentId(),
            ContentStatus.PUBLISHED.name(),
            ContentStatus.WITHDRAWN.name(),
            AuditEventResult.SUCCESS,
            CONTENT_WITHDRAWN_REASON,
            actor,
            approvedAt
        ));
    }
}
