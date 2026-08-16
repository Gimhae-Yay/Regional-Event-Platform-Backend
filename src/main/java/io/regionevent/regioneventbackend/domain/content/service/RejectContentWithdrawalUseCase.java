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
import io.regionevent.regioneventbackend.domain.content.entity.ContentWithdrawalRequest;
import io.regionevent.regioneventbackend.domain.content.entity.ContentWithdrawalRequestStatus;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.service.RegionService;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class RejectContentWithdrawalUseCase {

    private static final String CONTENT_WITHDRAWAL_REJECTED_REASON =
        "CONTENT_WITHDRAWAL_REJECTED";

    private final ContentWithdrawalRequestService contentWithdrawalRequestService;
    private final ContentService contentService;
    private final RegionService regionService;
    private final RegionAdminAuthorizationService regionAdminAuthorizationService;
    private final RecordAuditEventUseCase recordAuditEventUseCase;

    public RejectContentWithdrawalUseCase(
        ContentWithdrawalRequestService contentWithdrawalRequestService,
        ContentService contentService,
        RegionService regionService,
        RegionAdminAuthorizationService regionAdminAuthorizationService,
        RecordAuditEventUseCase recordAuditEventUseCase
    ) {
        this.contentWithdrawalRequestService = contentWithdrawalRequestService;
        this.contentService = contentService;
        this.regionService = regionService;
        this.regionAdminAuthorizationService = regionAdminAuthorizationService;
        this.recordAuditEventUseCase = recordAuditEventUseCase;
    }

    @Transactional
    public RejectContentWithdrawalResult reject(
        Long userId,
        Long withdrawalRequestId,
        String reason,
        UUID requestId
    ) {
        String normalizedReason = normalizeReason(reason);
        Long contentId = contentWithdrawalRequestService.findContentId(withdrawalRequestId);
        RegionAdminAuthorizationService.AuthorizedRegionAdmin regionAdmin =
            regionAdminAuthorizationService.requireAuthorizedRegionAdminForUpdate(userId);
        Long regionId = contentService.findContentRegionId(contentId);
        Region region = regionService.findRegionForUpdate(regionId);
        contentService.findForUpdate(contentId);
        UserRoleAssignment regionAdminAssignment = regionAdmin.authorize(regionId);
        ContentWithdrawalRequest withdrawalRequest =
            contentWithdrawalRequestService.findReviewTargetForUpdate(withdrawalRequestId);

        if (withdrawalRequest.getStatus() == ContentWithdrawalRequestStatus.REJECTED) {
            return resolveIdempotentResult(withdrawalRequest, normalizedReason);
        }
        if (withdrawalRequest.getStatus() != ContentWithdrawalRequestStatus.PENDING) {
            throw new BusinessException(ErrorCode.CONTENT_STATE_CONFLICT);
        }

        AuditEventActor actor = new AuditEventActor(regionAdminAssignment);
        Instant rejectedAt = contentService.findCurrentDatabaseTime();
        ContentWithdrawalRequest rejectedRequest = contentWithdrawalRequestService.reject(
            withdrawalRequest,
            actor.getAppUser(),
            rejectedAt,
            normalizedReason
        );
        recordAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            region,
            AuditEventTargetType.CONTENT_WITHDRAWAL_REQUEST,
            rejectedRequest.getContentWithdrawalRequestId(),
            ContentWithdrawalRequestStatus.PENDING.name(),
            ContentWithdrawalRequestStatus.REJECTED.name(),
            AuditEventResult.SUCCESS,
            CONTENT_WITHDRAWAL_REJECTED_REASON,
            actor,
            rejectedAt
        ));
        return RejectContentWithdrawalResult.from(rejectedRequest);
    }

    private RejectContentWithdrawalResult resolveIdempotentResult(
        ContentWithdrawalRequest request,
        String reason
    ) {
        if (!reason.equals(request.getRejectionReason())) {
            throw new BusinessException(ErrorCode.CONTENT_STATE_CONFLICT);
        }
        return RejectContentWithdrawalResult.from(request);
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return reason.strip();
    }
}
