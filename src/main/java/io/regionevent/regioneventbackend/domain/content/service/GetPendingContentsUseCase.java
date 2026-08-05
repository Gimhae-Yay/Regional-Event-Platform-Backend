package io.regionevent.regioneventbackend.domain.content.service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLog;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
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
public class GetPendingContentsUseCase {

    private static final Logger log = LoggerFactory.getLogger(GetPendingContentsUseCase.class);
    private static final String PENDING_STATUS = "PENDING";

    private final RegionAdminAuthorizationService regionAdminAuthorizationService;
    private final ContentService contentService;
    private final OriginalContentReviewTargetService originalContentReviewTargetService;
    private final RepresentativeImageViewUrlService representativeImageViewUrlService;

    public GetPendingContentsUseCase(
        RegionAdminAuthorizationService regionAdminAuthorizationService,
        ContentService contentService,
        OriginalContentReviewTargetService originalContentReviewTargetService,
        RepresentativeImageViewUrlService representativeImageViewUrlService
    ) {
        this.regionAdminAuthorizationService = regionAdminAuthorizationService;
        this.contentService = contentService;
        this.originalContentReviewTargetService = originalContentReviewTargetService;
        this.representativeImageViewUrlService = representativeImageViewUrlService;
    }

    @Transactional(readOnly = true)
    public PendingContentListResult get(Long authenticatedUserId, String status) {
        Long regionId = null;
        int resultCount = 0;
        try {
            regionId = regionAdminAuthorizationService.requireAuthorizedRegionId(authenticatedUserId);
            validateStatus(status);
            Long authorizedRegionId = regionId;

            List<PendingContentCandidate> candidates = originalContentReviewTargetService
                .findByContents(contentService.findPendingReviewContentsByRegionId(regionId))
                .stream()
                .filter(OriginalContentReviewTarget::isOriginalReviewTarget)
                .map(target -> validateAndCreateCandidate(target, authorizedRegionId))
                .sorted(Comparator
                    .comparing(PendingContentCandidate::submittedAt)
                    .thenComparing(candidate -> candidate.content().getContentId()))
                .toList();
            List<PendingContentListResult.Content> contents = candidates.stream()
                .map(this::toResult)
                .toList();
            resultCount = contents.size();
            logResult(regionId, resultCount, "SUCCESS");
            return new PendingContentListResult(contents);
        } catch (BusinessException exception) {
            logResult(regionId, resultCount, exception.getErrorCode().code());
            throw exception;
        } catch (RuntimeException exception) {
            logResult(regionId, resultCount, ErrorCode.INTERNAL_SERVER_ERROR.code());
            throw exception;
        }
    }

    private void validateStatus(String status) {
        if (!PENDING_STATUS.equals(status)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private PendingContentCandidate validateAndCreateCandidate(
        OriginalContentReviewTarget target,
        Long regionId
    ) {
        Content content = target.content();
        ContentLog pendingLog = target.pendingLog();
        AppUser operator = content.getOperator();
        ImageObject representativeImageObject = content.getRepresentativeImageObject();
        if (content.getContentId() == null
            || content.getDeletedAt() != null
            || content.getStatus() != ContentStatus.PENDING
            || !content.isScopedTo(regionId)
            || pendingLog == null
            || pendingLog.getDate() == null
            || operator == null
            || operator.getUserId() == null
            || operator.getName() == null
            || content.getRepresentativeImageAssignedAt() == null
            || representativeImageObject == null
            || representativeImageObject.getLifecycleStatus() != ImageLifecycleStatus.ACTIVE
            || representativeImageObject.getLinkedAt() == null
            || !representativeImageObject.isScopedTo(regionId)) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        return new PendingContentCandidate(content, pendingLog.getDate(), representativeImageObject);
    }

    private PendingContentListResult.Content toResult(PendingContentCandidate candidate) {
        Content content = candidate.content();
        RepresentativeImageViewUrl imageViewUrl = representativeImageViewUrlService
            .createViewUrl(candidate.representativeImageObject());
        return new PendingContentListResult.Content(
            content.getContentId(),
            content.getContentType(),
            content.getTitle(),
            content.getStatus(),
            content.getPublishAt(),
            candidate.submittedAt(),
            content.getOperator().getUserId(),
            content.getOperator().getName(),
            imageViewUrl.url(),
            imageViewUrl.expiresAt()
        );
    }

    private void logResult(Long regionId, int resultCount, String resultCode) {
        log.info(
            "Pending contents queried. requestId={}, regionId={}, resultCount={}, resultCode={}",
            RequestIdFilter.currentRequestId(),
            regionId,
            resultCount,
            resultCode
        );
    }

    private record PendingContentCandidate(
        Content content,
        Instant submittedAt,
        ImageObject representativeImageObject
    ) {
    }
}
