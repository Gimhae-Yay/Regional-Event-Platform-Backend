package io.regionevent.regioneventbackend.domain.content.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevision;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevisionStatus;
import io.regionevent.regioneventbackend.domain.image.entity.ImageObject;
import io.regionevent.regioneventbackend.domain.image.service.RepresentativeImageViewUrl;
import io.regionevent.regioneventbackend.domain.image.service.RepresentativeImageViewUrlService;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class GetLatestContentRevisionUseCase {

    private final ContentService contentService;
    private final ContentRevisionService contentRevisionService;
    private final OperatorAuthorizationService operatorAuthorizationService;
    private final RepresentativeImageViewUrlService representativeImageViewUrlService;

    public GetLatestContentRevisionUseCase(
        ContentService contentService,
        ContentRevisionService contentRevisionService,
        OperatorAuthorizationService operatorAuthorizationService,
        RepresentativeImageViewUrlService representativeImageViewUrlService
    ) {
        this.contentService = contentService;
        this.contentRevisionService = contentRevisionService;
        this.operatorAuthorizationService = operatorAuthorizationService;
        this.representativeImageViewUrlService = representativeImageViewUrlService;
    }

    @Transactional(readOnly = true)
    public LatestContentRevisionDetailResult get(Long authenticatedUserId, Long contentId) {
        Content content = contentService.findMyContentDetail(contentId);
        operatorAuthorizationService.authorizeOwnedContent(
            authenticatedUserId,
            content.getOperator(),
            content.getRegion()
        );

        ContentRevision revision = contentRevisionService.findLatestRevisionByContentId(contentId);
        validateCandidateImage(revision, content);
        validateReviewDetails(revision);
        RepresentativeImageViewUrl imageViewUrl = representativeImageViewUrlService.createViewUrl(
            revision.getCandidateImageObject()
        );
        return toResult(revision, contentId, imageViewUrl);
    }

    private void validateCandidateImage(ContentRevision revision, Content content) {
        ImageObject candidateImage = revision.getCandidateImageObject();
        if (candidateImage == null
            || revision.getCandidateImageAssignedAt() == null
            || !candidateImage.isScopedTo(content.getRegion().getRegionId())) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private void validateReviewDetails(ContentRevision revision) {
        boolean hasReviewedAt = revision.getReviewedAt() != null;
        boolean hasReviewedBy = revision.getReviewedBy() != null;
        boolean hasReviewReason = revision.getReviewReason() != null;

        if (revision.getStatus() == ContentRevisionStatus.EDIT_APPROVED) {
            validateReviewDetails(hasReviewedAt && hasReviewedBy && !hasReviewReason);
            return;
        }
        if (revision.getStatus() == ContentRevisionStatus.EDIT_REJECTED) {
            validateReviewDetails(
                hasReviewedAt
                    && hasReviewedBy
                    && hasReviewReason
                    && !revision.getReviewReason().isBlank()
            );
            return;
        }
        validateReviewDetails(!hasReviewedAt && !hasReviewedBy && !hasReviewReason);
    }

    private void validateReviewDetails(boolean valid) {
        if (!valid) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private LatestContentRevisionDetailResult toResult(
        ContentRevision revision,
        Long contentId,
        RepresentativeImageViewUrl imageViewUrl
    ) {
        return new LatestContentRevisionDetailResult(
            revision.getContentRevisionId(),
            contentId,
            revision.getRevisionNo(),
            revision.getBaseContentVersion(),
            revision.getStatus(),
            revision.getTitle(),
            revision.getDescription(),
            imageViewUrl.url(),
            imageViewUrl.expiresAt(),
            revision.getLocationText(),
            revision.getOperatingHoursText(),
            revision.getContactText(),
            revision.getPrecautions(),
            revision.getAgeRequirement(),
            revision.getMaterials(),
            revision.getCancellationPolicyText(),
            revision.getReservationPrice(),
            revision.getPublishAt(),
            revision.getReviewReason(),
            revision.getSubmittedAt(),
            revision.getReviewedAt()
        );
    }
}
