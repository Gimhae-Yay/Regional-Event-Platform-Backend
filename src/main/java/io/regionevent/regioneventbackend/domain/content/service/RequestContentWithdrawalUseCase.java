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
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentWithdrawalRequest;
import io.regionevent.regioneventbackend.domain.content.entity.ContentWithdrawalRequestStatus;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.region.service.RegionService;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService.AuthorizedOperator;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class RequestContentWithdrawalUseCase {

    private static final String REQUESTED_REASON_CODE = "CONTENT_WITHDRAWAL_REQUESTED";

    private final OperatorAuthorizationService operatorAuthorizationService;
    private final RegionService regionService;
    private final ContentService contentService;
    private final ContentWithdrawalRequestService contentWithdrawalRequestService;
    private final ContentWithdrawalRequestHasher contentWithdrawalRequestHasher;
    private final RecordAuditEventUseCase recordAuditEventUseCase;

    public RequestContentWithdrawalUseCase(
        OperatorAuthorizationService operatorAuthorizationService,
        RegionService regionService,
        ContentService contentService,
        ContentWithdrawalRequestService contentWithdrawalRequestService,
        ContentWithdrawalRequestHasher contentWithdrawalRequestHasher,
        RecordAuditEventUseCase recordAuditEventUseCase
    ) {
        this.operatorAuthorizationService = operatorAuthorizationService;
        this.regionService = regionService;
        this.contentService = contentService;
        this.contentWithdrawalRequestService = contentWithdrawalRequestService;
        this.contentWithdrawalRequestHasher = contentWithdrawalRequestHasher;
        this.recordAuditEventUseCase = recordAuditEventUseCase;
    }

    @Transactional
    public RequestContentWithdrawalResult request(
        Long userId,
        Long contentId,
        String idempotencyKey,
        String reason,
        UUID requestId
    ) {
        String normalizedReason = normalizeReason(reason);
        validateIdempotencyKey(idempotencyKey);
        String idempotencyKeyHash = contentWithdrawalRequestHasher.hashIdempotencyKey(
            idempotencyKey
        );

        AuthorizedOperator operator =
            operatorAuthorizationService.requireAuthorizedOperatorForUpdate(userId);
        Long regionId = contentService.findContentRegionId(contentId);
        Region region = regionService.findRegionForUpdate(regionId);
        Content content = contentService.findWithdrawalRequestTargetForUpdate(contentId);
        validateOwnership(operator, content);

        ContentWithdrawalRequest sameKeyRequest = contentWithdrawalRequestService
            .findByIdempotencyKeyForUpdate(contentId, idempotencyKeyHash)
            .orElse(null);
        if (sameKeyRequest != null) {
            if (!sameKeyRequest.getRequestReason().equals(normalizedReason)) {
                throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
            }
            return RequestContentWithdrawalResult.from(sameKeyRequest);
        }

        if (content.getStatus() != ContentStatus.PUBLISHED) {
            throw new BusinessException(ErrorCode.CONTENT_STATE_CONFLICT);
        }
        if (contentWithdrawalRequestService.findPendingForUpdate(contentId).isPresent()) {
            throw new BusinessException(ErrorCode.CONTENT_STATE_CONFLICT);
        }

        Instant requestedAt = contentService.findCurrentDatabaseTime();
        ContentWithdrawalRequest request = contentWithdrawalRequestService.createPending(
            content,
            operator.user(),
            idempotencyKeyHash,
            normalizedReason,
            requestedAt
        );
        recordAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            region,
            AuditEventTargetType.CONTENT_WITHDRAWAL_REQUEST,
            request.getContentWithdrawalRequestId(),
            null,
            ContentWithdrawalRequestStatus.PENDING.name(),
            AuditEventResult.SUCCESS,
            REQUESTED_REASON_CODE,
            new AuditEventActor(operator.roleAssignment()),
            requestedAt
        ));
        return RequestContentWithdrawalResult.from(request);
    }

    private void validateOwnership(AuthorizedOperator operator, Content content) {
        if (!content.isOwnedBy(operator.user().getUserId())
            || !content.isScopedTo(operator.region().getRegionId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return reason.strip();
    }
}
