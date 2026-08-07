package io.regionevent.regioneventbackend.domain.content.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.content.entity.ContentRevision;
import io.regionevent.regioneventbackend.domain.image.entity.ImageLifecycleStatus;
import io.regionevent.regioneventbackend.domain.image.entity.ImageObject;
import io.regionevent.regioneventbackend.domain.image.service.RepresentativeImageViewUrl;
import io.regionevent.regioneventbackend.domain.image.service.RepresentativeImageViewUrlService;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class GetPendingContentRevisionsUseCase {

    private static final Logger log = LoggerFactory.getLogger(GetPendingContentRevisionsUseCase.class);
    private static final String EDIT_REQUESTED_STATUS = "EDIT_REQUESTED";

    private final RegionAdminAuthorizationService regionAdminAuthorizationService;
    private final ContentRevisionService contentRevisionService;
    private final OriginalContentReviewTargetService originalContentReviewTargetService;
    private final ContentRevisionReviewTypePolicy contentRevisionReviewTypePolicy;
    private final RepresentativeImageViewUrlService representativeImageViewUrlService;

    public GetPendingContentRevisionsUseCase(
        RegionAdminAuthorizationService regionAdminAuthorizationService,
        ContentRevisionService contentRevisionService,
        OriginalContentReviewTargetService originalContentReviewTargetService,
        ContentRevisionReviewTypePolicy contentRevisionReviewTypePolicy,
        RepresentativeImageViewUrlService representativeImageViewUrlService
    ) {
        this.regionAdminAuthorizationService = regionAdminAuthorizationService;
        this.contentRevisionService = contentRevisionService;
        this.originalContentReviewTargetService = originalContentReviewTargetService;
        this.contentRevisionReviewTypePolicy = contentRevisionReviewTypePolicy;
        this.representativeImageViewUrlService = representativeImageViewUrlService;
    }

    @Transactional(readOnly = true)
    public PendingContentRevisionListResult get(Long authenticatedUserId, String status) {
        Long regionId = null;
        int resultCount = 0;
        try {
            regionId = regionAdminAuthorizationService.requireAuthorizedRegionId(authenticatedUserId);
            validateStatus(status);
            Long authorizedRegionId = regionId;
            List<ClassifiedCandidate> candidates = contentRevisionService
                .findReviewCandidatesByRegionId(regionId)
                .stream()
                .map(candidate -> classifyAndValidate(candidate, authorizedRegionId))
                .toList();
            List<PendingContentRevisionListResult.Revision> revisions = candidates.stream()
                .map(this::toResult)
                .toList();
            resultCount = revisions.size();
            logResult(regionId, resultCount, "SUCCESS");
            return new PendingContentRevisionListResult(revisions);
        } catch (BusinessException exception) {
            logResult(regionId, resultCount, exception.getErrorCode().code());
            throw exception;
        } catch (RuntimeException exception) {
            logResult(regionId, resultCount, ErrorCode.INTERNAL_SERVER_ERROR.code());
            throw exception;
        }
    }

    private void validateStatus(String status) {
        if (!EDIT_REQUESTED_STATUS.equals(status)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private ClassifiedCandidate classifyAndValidate(
        ContentRevisionReviewCandidate candidate,
        Long regionId
    ) {
        boolean isPrePublicationRevisionByHistory = originalContentReviewTargetService
            .findByContentId(candidate.content().getContentId())
            .map(target -> target.type() == OriginalContentReviewTargetType.PRE_PUBLICATION_REVISION)
            .orElse(false);
        ContentRevisionReviewType reviewType = contentRevisionReviewTypePolicy.classify(
            candidate,
            isPrePublicationRevisionByHistory
        );
        validateCandidate(candidate, regionId);
        return new ClassifiedCandidate(candidate, reviewType);
    }

    private void validateCandidate(ContentRevisionReviewCandidate candidate, Long regionId) {
        ContentRevision revision = candidate.revision();
        AppUser operator = candidate.operator();
        ImageObject imageObject = candidate.candidateImageObject();
        if (revision.getCandidateImageAssignedAt() == null
            || operator == null
            || operator.getUserId() == null
            || operator.getName() == null
            || imageObject.getLifecycleStatus() != ImageLifecycleStatus.ACTIVE
            || imageObject.getLinkedAt() == null
            || !imageObject.isScopedTo(regionId)) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private PendingContentRevisionListResult.Revision toResult(ClassifiedCandidate classifiedCandidate) {
        ContentRevisionReviewCandidate candidate = classifiedCandidate.candidate();
        ContentRevision revision = candidate.revision();
        AppUser operator = candidate.operator();
        RepresentativeImageViewUrl imageViewUrl = representativeImageViewUrlService
            .createViewUrl(candidate.candidateImageObject());
        return new PendingContentRevisionListResult.Revision(
            revision.getContentRevisionId(),
            candidate.content().getContentId(),
            classifiedCandidate.reviewType(),
            candidate.content().getStatus(),
            revision.getTitle(),
            revision.getPublishAt(),
            revision.getSubmittedAt(),
            operator.getUserId(),
            operator.getName(),
            imageViewUrl.url(),
            imageViewUrl.expiresAt()
        );
    }

    private void logResult(Long regionId, int resultCount, String resultCode) {
        log.info(
            "Pending content revisions queried. requestId={}, regionId={}, resultCount={}, resultCode={}",
            RequestIdFilter.currentRequestId(),
            regionId,
            resultCount,
            resultCode
        );
    }

    private record ClassifiedCandidate(
        ContentRevisionReviewCandidate candidate,
        ContentRevisionReviewType reviewType
    ) {
    }
}
