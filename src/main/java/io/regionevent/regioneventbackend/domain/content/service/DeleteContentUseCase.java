package io.regionevent.regioneventbackend.domain.content.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventActor;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.audit.service.RecordFailedAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLogStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.image.entity.ImageObject;
import io.regionevent.regioneventbackend.domain.image.service.ImageObjectCleanupService;
import io.regionevent.regioneventbackend.domain.image.service.ImageObjectService;
import io.regionevent.regioneventbackend.domain.image.service.ImageObjectService.DeletePendingImageObject;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class DeleteContentUseCase {

    private static final Logger log = LoggerFactory.getLogger(DeleteContentUseCase.class);

    private final ContentService contentService;
    private final ContentLogService contentLogService;
    private final ImageObjectService imageObjectService;
    private final ImageObjectCleanupService imageObjectCleanupService;
    private final RegionAdminAuthorizationService regionAdminAuthorizationService;
    private final RecordAuditEventUseCase recordAuditEventUseCase;
    private final RecordFailedAuditEventUseCase recordFailedAuditEventUseCase;
    private final Clock clock;

    public DeleteContentUseCase(
        ContentService contentService,
        ContentLogService contentLogService,
        ImageObjectService imageObjectService,
        ImageObjectCleanupService imageObjectCleanupService,
        RegionAdminAuthorizationService regionAdminAuthorizationService,
        RecordAuditEventUseCase recordAuditEventUseCase,
        RecordFailedAuditEventUseCase recordFailedAuditEventUseCase,
        Clock clock
    ) {
        this.contentService = contentService;
        this.contentLogService = contentLogService;
        this.imageObjectService = imageObjectService;
        this.imageObjectCleanupService = imageObjectCleanupService;
        this.regionAdminAuthorizationService = regionAdminAuthorizationService;
        this.recordAuditEventUseCase = recordAuditEventUseCase;
        this.recordFailedAuditEventUseCase = recordFailedAuditEventUseCase;
        this.clock = clock;
    }

    @Transactional
    public DeleteContentResult delete(
        Long userId,
        Long contentId,
        String reason,
        UUID requestId
    ) {
        Content content = null;
        try {
            String normalizedReason = normalizeReason(reason);
            RegionAdminAuthorizationService.AuthorizedRegionAdmin regionAdmin =
                regionAdminAuthorizationService.requireAuthorizedRegionAdminForUpdate(userId);
            content = contentService.findDeletionTargetForUpdate(contentId);
            UserRoleAssignment assignment = regionAdmin.authorize(
                content.getRegion().getRegionId()
            );
            validateDeletable(content);
            ContentStatus previousStatus = content.getStatus();
            ImageObject representativeImageObject = lockRepresentativeImage(content);
            Instant deletedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);

            ImageObject detachedImageObject = contentService.softDelete(content, deletedAt);
            validateSameImage(representativeImageObject, detachedImageObject);
            DeletePendingImageObject deletePendingImageObject = imageObjectService
                .markDeletePendingIfUnreferenced(representativeImageObject)
                .orElse(null);
            contentLogService.recordDeleted(
                content,
                assignment.getAppUser(),
                deletedAt,
                normalizedReason
            );
            recordSuccess(
                requestId,
                content,
                previousStatus,
                new AuditEventActor(assignment),
                deletedAt
            );
            if (deletePendingImageObject != null) {
                deleteImageAfterCommit(deletePendingImageObject);
            }
            return DeleteContentResult.from(content, deletedAt, normalizedReason);
        } catch (BusinessException exception) {
            recordFailure(requestId, content, exception.getErrorCode());
            throw exception;
        } catch (RuntimeException exception) {
            recordFailure(requestId, content, ErrorCode.INTERNAL_SERVER_ERROR);
            throw exception;
        }
    }

    private ImageObject lockRepresentativeImage(Content content) {
        ImageObject representativeImageObject = content.getRepresentativeImageObject();
        if (representativeImageObject == null) {
            throw new IllegalStateException("content representative image must exist before deletion");
        }
        return imageObjectService.findActiveForUpdate(representativeImageObject.getImageObjectId());
    }

    private void validateDeletable(Content content) {
        if ((content.getStatus() != ContentStatus.PENDING
            && content.getStatus() != ContentStatus.APPROVED)
            || content.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.CONTENT_DELETE_CONFLICT);
        }
    }

    private void validateSameImage(ImageObject lockedImageObject, ImageObject detachedImageObject) {
        if (!lockedImageObject.getImageObjectId().equals(detachedImageObject.getImageObjectId())) {
            throw new IllegalStateException("locked image object must match detached representative image");
        }
    }

    private void recordSuccess(
        UUID requestId,
        Content content,
        ContentStatus previousStatus,
        AuditEventActor actor,
        Instant deletedAt
    ) {
        recordAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            content.getRegion(),
            AuditEventTargetType.CONTENT,
            content.getContentId(),
            previousStatus.name(),
            ContentLogStatus.DELETED.name(),
            AuditEventResult.SUCCESS,
            null,
            actor,
            deletedAt
        ));
    }

    private void recordFailure(UUID requestId, Content content, ErrorCode errorCode) {
        recordFailedAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            content == null ? null : content.getRegion(),
            AuditEventTargetType.CONTENT,
            content == null ? null : content.getContentId(),
            content == null ? null : content.getStatus().name(),
            null,
            AuditEventResult.FAILURE,
            errorCode.code(),
            null,
            clock.instant().truncatedTo(ChronoUnit.MICROS)
        ));
        log.warn(
            "콘텐츠 삭제를 거부했습니다. requestId={}, contentId={}, errorCode={}",
            requestId,
            content == null ? null : content.getContentId(),
            errorCode.code()
        );
    }

    private void deleteImageAfterCommit(DeletePendingImageObject imageObject) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

            @Override
            public void afterCommit() {
                imageObjectCleanupService.deletePendingObject(
                    imageObject.imageObjectId(),
                    imageObject.objectKey()
                );
            }
        });
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return reason.strip();
    }
}
