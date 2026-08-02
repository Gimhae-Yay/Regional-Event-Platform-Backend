package io.regionevent.regioneventbackend.domain.content.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevision;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.image.entity.ImageObject;
import io.regionevent.regioneventbackend.domain.image.service.RepresentativeImageViewUrl;
import io.regionevent.regioneventbackend.domain.image.service.RepresentativeImageViewUrlService;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class GetContentRevisionReviewDetailUseCase {

    private static final Logger log = LoggerFactory.getLogger(GetContentRevisionReviewDetailUseCase.class);

    private final RegionAdminAuthorizationService regionAdminAuthorizationService;
    private final ContentRevisionService contentRevisionService;
    private final OriginalContentReviewTargetService originalContentReviewTargetService;
    private final ContentRevisionReviewTypePolicy contentRevisionReviewTypePolicy;
    private final ContentSessionService contentSessionService;
    private final RepresentativeImageViewUrlService representativeImageViewUrlService;

    public GetContentRevisionReviewDetailUseCase(
        RegionAdminAuthorizationService regionAdminAuthorizationService,
        ContentRevisionService contentRevisionService,
        OriginalContentReviewTargetService originalContentReviewTargetService,
        ContentRevisionReviewTypePolicy contentRevisionReviewTypePolicy,
        ContentSessionService contentSessionService,
        RepresentativeImageViewUrlService representativeImageViewUrlService
    ) {
        this.regionAdminAuthorizationService = regionAdminAuthorizationService;
        this.contentRevisionService = contentRevisionService;
        this.originalContentReviewTargetService = originalContentReviewTargetService;
        this.contentRevisionReviewTypePolicy = contentRevisionReviewTypePolicy;
        this.contentSessionService = contentSessionService;
        this.representativeImageViewUrlService = representativeImageViewUrlService;
    }

    @Transactional(readOnly = true)
    public ContentRevisionReviewDetailResult get(Long authenticatedUserId, Long revisionId) {
        Long regionId = null;
        try {
            regionId = regionAdminAuthorizationService.requireAuthorizedRegionId(authenticatedUserId);
            ContentRevisionReviewCandidate candidate = contentRevisionService.findReviewCandidateById(revisionId);
            validateRegion(candidate.content(), regionId);

            boolean isPrePublicationRevisionByHistory = originalContentReviewTargetService
                .findByContentId(candidate.content().getContentId())
                .map(target -> target.type() == OriginalContentReviewTargetType.PRE_PUBLICATION_REVISION)
                .orElse(false);
            ContentRevisionReviewType reviewType = contentRevisionReviewTypePolicy.classify(
                candidate,
                isPrePublicationRevisionByHistory
            );
            validateCandidateImage(candidate, regionId);

            List<ContentSession> sessions = contentSessionService
                .findCurrentSessionsByContentId(candidate.content().getContentId());
            if (sessions.isEmpty()) {
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
            }

            RepresentativeImageViewUrl imageViewUrl = representativeImageViewUrlService
                .createViewUrl(candidate.candidateImageObject());
            ContentRevisionReviewDetailResult result = toResult(
                candidate,
                reviewType,
                imageViewUrl,
                sessions
            );
            logResult(regionId, revisionId, "SUCCESS");
            return result;
        } catch (BusinessException exception) {
            logResult(regionId, revisionId, exception.getErrorCode().code());
            throw exception;
        } catch (RuntimeException exception) {
            logResult(regionId, revisionId, ErrorCode.INTERNAL_SERVER_ERROR.code());
            throw exception;
        }
    }

    private void validateRegion(Content content, Long authorizedRegionId) {
        if (!content.getRegion().getRegionId().equals(authorizedRegionId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private void validateCandidateImage(ContentRevisionReviewCandidate candidate, Long regionId) {
        ImageObject imageObject = candidate.candidateImageObject();
        if (candidate.revision().getCandidateImageAssignedAt() == null
            || !imageObject.isScopedTo(regionId)) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private ContentRevisionReviewDetailResult toResult(
        ContentRevisionReviewCandidate candidate,
        ContentRevisionReviewType reviewType,
        RepresentativeImageViewUrl imageViewUrl,
        List<ContentSession> sessions
    ) {
        ContentRevision revision = candidate.revision();
        Content content = candidate.content();
        return new ContentRevisionReviewDetailResult(
            revision.getContentRevisionId(),
            content.getContentId(),
            reviewType,
            content.getStatus(),
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
            revision.getPublishAt(),
            sessions.stream()
                .map(this::toSession)
                .toList(),
            revision.getSubmittedAt()
        );
    }

    private ContentRevisionReviewDetailResult.Session toSession(ContentSession session) {
        return new ContentRevisionReviewDetailResult.Session(
            session.getSessionId(),
            session.getStatus(),
            session.getStartsAt(),
            session.getEndsAt(),
            session.getCheckinOpenAt(),
            session.getCheckinCloseAt(),
            session.getCapacity(),
            session.getRemainingCapacity()
        );
    }

    private void logResult(Long regionId, Long revisionId, String resultCode) {
        log.info(
            "Content revision review detail queried. requestId={}, regionId={}, revisionId={}, resultCode={}",
            RequestIdFilter.currentRequestId(),
            regionId,
            revisionId,
            resultCode
        );
    }
}
