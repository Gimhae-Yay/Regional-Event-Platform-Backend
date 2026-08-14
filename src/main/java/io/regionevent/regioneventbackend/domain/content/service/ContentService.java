package io.regionevent.regioneventbackend.domain.content.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRepository;
import io.regionevent.regioneventbackend.domain.content.repository.MyContentProjection;
import io.regionevent.regioneventbackend.domain.content.repository.PublicContentDetailVerificationProjection;
import io.regionevent.regioneventbackend.domain.content.repository.PublicContentListVerificationProjection;
import io.regionevent.regioneventbackend.domain.content.repository.RegionHomeContentSessionVerificationProjection;
import io.regionevent.regioneventbackend.domain.image.entity.ImageObject;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class ContentService {

    private final ContentRepository contentRepository;

    public ContentService(ContentRepository contentRepository) {
        this.contentRepository = contentRepository;
    }

    public Content createPendingContent(
        Region region,
        AppUser operator,
        ImageObject representativeImageObject,
        CreateContentCommand command,
        Instant representativeImageAssignedAt
    ) {
        Content content = new Content(
            region,
            operator,
            ContentType.EVENT_EXPERIENCE,
            ContentStatus.PENDING,
            command.title(),
            command.description(),
            command.locationText(),
            command.operatingHoursText(),
            command.contactText(),
            command.precautions(),
            command.ageRequirement(),
            command.materials(),
            command.cancellationPolicyText(),
            command.reservationPrice(),
            command.publishAt()
        );
        content.assignRepresentativeImage(representativeImageObject, representativeImageAssignedAt);
        return contentRepository.saveAndFlush(content);
    }

    public Content findOwnedContentForRevisionCreation(
        Long contentId,
        Long operatorUserId,
        Long regionId
    ) {
        validateRequiredId(contentId);
        validateRequiredId(operatorUserId);
        validateRequiredId(regionId);

        Content content = contentRepository.findByContentIdAndDeletedAtIsNull(contentId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!content.isOwnedBy(operatorUserId) || !content.isScopedTo(regionId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return content;
    }

    public List<Content> findOwnedContentsForStampbookCreation(
        List<Long> contentIds,
        Long operatorUserId,
        Long regionId
    ) {
        if (contentIds == null || contentIds.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        List<Content> contents = new ArrayList<>(contentIds.size());
        for (Long contentId : contentIds) {
            contents.add(findOwnedContentForRevisionCreation(contentId, operatorUserId, regionId));
        }
        return List.copyOf(contents);
    }

    public List<Content> findMissionTargetContentsForUpdate(
        List<Long> contentIds,
        Long regionId
    ) {
        if (contentIds == null || contentIds.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        validateRequiredId(regionId);

        List<Content> contents = contentRepository.findMissionTargetsForUpdate(contentIds);
        if (contents.size() != contentIds.size()) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (contents.stream().anyMatch(content -> !content.isScopedTo(regionId))) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return List.copyOf(contents);
    }

    public List<Content> findStampbookTargetContentsForUpdate(List<Long> contentIds) {
        if (contentIds == null || contentIds.isEmpty()) {
            throw new BusinessException(ErrorCode.STAMPBOOK_STATE_CONFLICT);
        }

        List<Content> contents = contentRepository.findStampbookTargetsForUpdate(contentIds);
        if (contents.size() != contentIds.size()) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return List.copyOf(contents);
    }

    public Content markPrePublicationRevisionPending(Content content) {
        if (content.getStatus() != ContentStatus.APPROVED) {
            throw new BusinessException(ErrorCode.CONTENT_STATE_CONFLICT);
        }
        content.requestPrePublicationRevision();
        return contentRepository.saveAndFlush(content);
    }

    public Content findRejectedOwnedContentForUpdate(
        Long contentId,
        Long operatorUserId,
        Long regionId
    ) {
        validateRequiredId(contentId);
        validateRequiredId(operatorUserId);
        validateRequiredId(regionId);

        Content content = contentRepository.findByContentIdAndDeletedAtIsNull(contentId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!content.isOwnedBy(operatorUserId) || !content.isScopedTo(regionId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (content.getStatus() != ContentStatus.REJECTED) {
            throw new BusinessException(ErrorCode.CONTENT_STATE_CONFLICT);
        }
        return content;
    }

    public Content updateRejectedContent(
        Content content,
        UpdateContentCommand command,
        ImageObject replacementImageObject,
        Instant representativeImageAssignedAt
    ) {
        if (content.getStatus() != ContentStatus.REJECTED) {
            throw new BusinessException(ErrorCode.CONTENT_STATE_CONFLICT);
        }
        content.replaceEditableFields(
            command.title(),
            command.description(),
            command.locationText(),
            command.operatingHoursText(),
            command.contactText(),
            command.precautions(),
            command.ageRequirement(),
            command.materials(),
            command.cancellationPolicyText(),
            command.reservationPrice(),
            command.publishAt()
        );
        if (replacementImageObject != null) {
            content.assignRepresentativeImage(replacementImageObject, representativeImageAssignedAt);
        }
        return contentRepository.saveAndFlush(content);
    }

    public boolean existsPublicPublishedAndNotDeletedById(Long contentId) {
        return contentRepository.existsByContentIdAndStatusAndDeletedAtIsNullAndRegionIsPublicTrue(
            contentId,
            ContentStatus.PUBLISHED
        );
    }

    public boolean hasOwnedContent(Long userId) {
        return contentRepository.existsByOperatorUserId(userId);
    }

    public boolean hasUndeletedContentInRegion(Long regionId) {
        return contentRepository.existsByRegionRegionIdAndDeletedAtIsNull(regionId);
    }

    public Content findPublicContent(Long contentId) {
        validateRequiredId(contentId);
        return contentRepository.findPublicContentByContentId(
            contentId,
            ContentStatus.PUBLISHED
        ).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    public PublicContentDetailVerificationProjection findPublicContentDetailVerification(Long contentId) {
        validateRequiredId(contentId);
        return contentRepository.findPublicContentDetailVerification(
            contentId,
            ContentStatus.PUBLISHED
        ).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    public PublicContentStaticInfo findPublicContentStaticInfo(
        Long regionId,
        Long contentId,
        int versionNo
    ) {
        return contentRepository.findPublicContentStaticInfo(
            regionId,
            contentId,
            versionNo,
            ContentStatus.PUBLISHED
        ).map(PublicContentStaticInfo::from)
            .orElseThrow(() -> new IllegalStateException("public content static info must exist after verification"));
    }

    public Content findMyContentDetail(Long contentId) {
        validateRequiredId(contentId);
        return contentRepository.findDetailByContentIdAndDeletedAtIsNull(contentId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public Content findOperatorReservationListTarget(Long contentId) {
        validateRequiredId(contentId);
        return contentRepository.findOperatorReservationListTarget(contentId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    public List<PublicContentListVerificationProjection> findPublicContentListVerifications(
        Long regionId,
        ContentType contentType,
        Boolean reservationAvailable
    ) {
        return contentRepository.findPublicContentListVerifications(
            regionId,
            contentType,
            reservationAvailable,
            ContentStatus.PUBLISHED,
            ContentSessionStatus.SCHEDULED
        );
    }

    public List<RegionHomeContentSessionVerificationProjection> findRegionHomeContentSessionVerifications(
        Long regionId
    ) {
        validateRequiredId(regionId);
        return contentRepository.findRegionHomeContentSessionVerifications(
            regionId,
            ContentStatus.PUBLISHED,
            ContentSessionStatus.SCHEDULED
        );
    }

    public List<MyContentProjection> findMyContents(Long operatorUserId, Long regionId) {
        validateRequiredId(operatorUserId);
        validateRequiredId(regionId);
        return contentRepository.findMyContents(operatorUserId, regionId);
    }

    @Transactional(readOnly = true)
    public List<Content> findPendingReviewContentsByRegionId(Long regionId) {
        validateRequiredId(regionId);
        return contentRepository.findByRegionRegionIdAndStatusAndDeletedAtIsNullOrderByContentIdAsc(
            regionId,
            ContentStatus.PENDING
        );
    }

    public Content findApprovalTargetForUpdate(Long contentId) {
        return contentRepository.findApprovalTargetForUpdate(contentId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    public Content findEndTargetForUpdate(Long contentId) {
        return contentRepository.findEndTargetForUpdate(contentId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Long findSuspendTargetRegionId(Long contentId) {
        validateRequiredId(contentId);
        return contentRepository.findRegionIdByContentIdAndDeletedAtIsNull(contentId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public List<Long> findAutoEndCandidateIds(List<ContentSessionStatus> terminalStatuses) {
        return contentRepository.findAutoEndCandidateIds(ContentStatus.PUBLISHED, terminalStatuses);
    }

    public Content findDeletionTargetForUpdate(Long contentId) {
        validateRequiredId(contentId);
        return contentRepository.findDeletionTargetForUpdate(contentId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    public Content findForUpdate(Long contentId) {
        validateRequiredId(contentId);
        return contentRepository.findByContentIdForUpdate(contentId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    public Content findSuspendTargetForUpdate(Long contentId) {
        return contentRepository.findSuspendTargetForUpdate(contentId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    public List<Long> findApprovedPublicationCandidateIds() {
        return contentRepository.findApprovedPublicationCandidateIds();
    }

    public Optional<Content> findApprovedPublicationTargetForUpdate(Long contentId) {
        validateRequiredId(contentId);
        return contentRepository.findApprovedPublicationTargetForUpdate(contentId);
    }

    public Instant findCurrentDatabaseTime() {
        return toInstant(contentRepository.findCurrentEpochSeconds());
    }

    public boolean lockPublishedReservationTarget(Long contentId) {
        return contentRepository.findPublishedReservationTargetIdForUpdate(contentId).isPresent();
    }

    public boolean lockPublishedCapacityHoldTarget(Long contentId) {
        return contentRepository.findPublishedCapacityHoldTargetIdForUpdate(contentId).isPresent();
    }

    public Optional<Long> findPublishedPaymentReservationPriceForUpdate(Long contentId) {
        return contentRepository.findPublishedPaymentReservationPriceForUpdate(contentId);
    }

    public Content approve(Content content) {
        content.approve();
        return contentRepository.saveAndFlush(content);
    }

    public Content publish(Content content) {
        content.publish();
        return contentRepository.saveAndFlush(content);
    }

    public Content reject(Content content, Instant rejectedAt) {
        int updatedCount = contentRepository.rejectPendingByContentId(
            content.getContentId(),
            rejectedAt
        );
        if (updatedCount != 1) {
            throw new BusinessException(ErrorCode.CONTENT_STATE_CONFLICT);
        }
        content.reject();
        return content;
    }

    public void validateSubmitRequirements(Content content) {
        if (content.getRepresentativeImageObject() == null
            || isBlank(content.getTitle())
            || isBlank(content.getDescription())
            || isBlank(content.getLocationText())
            || isBlank(content.getOperatingHoursText())
            || isBlank(content.getContactText())
            || isBlank(content.getPrecautions())
            || isBlank(content.getAgeRequirement())
            || isBlank(content.getMaterials())
            || isBlank(content.getCancellationPolicyText())
            || content.getReservationPrice() < 0
            || content.getPublishAt() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    public Content submitForReview(Content content, Instant submittedAt) {
        int updatedCount = contentRepository.submitRejectedByContentId(
            content.getContentId(),
            submittedAt
        );
        if (updatedCount != 1) {
            throw new BusinessException(ErrorCode.CONTENT_STATE_CONFLICT);
        }
        content.submitForReview();
        return content;
    }

    public Content end(Content content, Instant endedAt) {
        int updatedCount = contentRepository.endPublishedByContentId(
            content.getContentId(),
            endedAt
        );
        if (updatedCount != 1) {
            throw new BusinessException(ErrorCode.CONTENT_END_CONFLICT);
        }
        content.end();
        return content;
    }

    public ImageObject softDelete(Content content, Instant deletedAt) {
        if ((content.getStatus() != ContentStatus.PENDING
            && content.getStatus() != ContentStatus.APPROVED)
            || content.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.CONTENT_DELETE_CONFLICT);
        }
        content.softDelete(deletedAt);
        ImageObject detachedImageObject = content.detachRepresentativeImage();
        if (detachedImageObject == null) {
            throw new IllegalStateException("content representative image must exist before deletion");
        }
        contentRepository.saveAndFlush(content);
        return detachedImageObject;
    }

    public Content suspend(Content content, Instant suspendedAt) {
        int updatedCount = contentRepository.suspendPublishedByContentId(
            content.getContentId(),
            suspendedAt
        );
        if (updatedCount != 1) {
            throw new BusinessException(ErrorCode.CONTENT_SUSPEND_CONFLICT);
        }
        content.suspend();
        return content;
    }

    private Instant toInstant(BigDecimal epochSeconds) {
        long seconds = epochSeconds.longValue();
        return Instant.ofEpochSecond(seconds, epochSeconds.remainder(BigDecimal.ONE).movePointRight(9).longValue());
    }

    private void validateRequiredId(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record CreateContentCommand(
        String title,
        String description,
        String locationText,
        String operatingHoursText,
        String contactText,
        String precautions,
        String ageRequirement,
        String materials,
        String cancellationPolicyText,
        long reservationPrice,
        Instant publishAt
    ) {

        public CreateContentCommand(
            String title,
            String description,
            String locationText,
            String operatingHoursText,
            String contactText,
            String precautions,
            String ageRequirement,
            String materials,
            String cancellationPolicyText,
            Instant publishAt
        ) {
            this(
                title, description, locationText, operatingHoursText, contactText, precautions,
                ageRequirement, materials, cancellationPolicyText, 0, publishAt
            );
        }
    }

    public record UpdateContentCommand(
        String title,
        String description,
        String locationText,
        String operatingHoursText,
        String contactText,
        String precautions,
        String ageRequirement,
        String materials,
        String cancellationPolicyText,
        long reservationPrice,
        Instant publishAt
    ) {

        public UpdateContentCommand(
            String title,
            String description,
            String locationText,
            String operatingHoursText,
            String contactText,
            String precautions,
            String ageRequirement,
            String materials,
            String cancellationPolicyText,
            Instant publishAt
        ) {
            this(
                title, description, locationText, operatingHoursText, contactText, precautions,
                ageRequirement, materials, cancellationPolicyText, 0, publishAt
            );
        }
    }
}
