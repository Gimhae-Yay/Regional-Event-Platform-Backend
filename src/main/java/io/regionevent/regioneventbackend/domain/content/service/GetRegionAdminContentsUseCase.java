package io.regionevent.regioneventbackend.domain.content.service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLog;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLogStatus;
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
public class GetRegionAdminContentsUseCase {

    private static final Logger log = LoggerFactory.getLogger(GetRegionAdminContentsUseCase.class);

    private final RegionAdminAuthorizationService regionAdminAuthorizationService;
    private final ContentService contentService;
    private final ContentLogService contentLogService;
    private final OriginalContentReviewTargetService originalContentReviewTargetService;
    private final RepresentativeImageViewUrlService representativeImageViewUrlService;

    public GetRegionAdminContentsUseCase(
        RegionAdminAuthorizationService regionAdminAuthorizationService,
        ContentService contentService,
        ContentLogService contentLogService,
        OriginalContentReviewTargetService originalContentReviewTargetService,
        RepresentativeImageViewUrlService representativeImageViewUrlService
    ) {
        this.regionAdminAuthorizationService = regionAdminAuthorizationService;
        this.contentService = contentService;
        this.contentLogService = contentLogService;
        this.originalContentReviewTargetService = originalContentReviewTargetService;
        this.representativeImageViewUrlService = representativeImageViewUrlService;
    }

    @Transactional(readOnly = true)
    public RegionAdminContentListResult get(Long authenticatedUserId, String status) {
        Long regionId = null;
        int resultCount = 0;
        try {
            regionId = regionAdminAuthorizationService.requireAuthorizedRegionId(authenticatedUserId);
            ContentStatus requestedStatus = toContentStatus(status);
            Long authorizedRegionId = regionId;

            List<RegionAdminContentCandidate> candidates = findCandidates(
                authorizedRegionId,
                requestedStatus
            );
            List<RegionAdminContentListResult.Content> contents = candidates.stream()
                .map(candidate -> toResult(candidate, requestedStatus))
                .toList();
            resultCount = contents.size();
            logResult(regionId, status, resultCount, "SUCCESS");
            return new RegionAdminContentListResult(requestedStatus, contents);
        } catch (BusinessException exception) {
            logResult(regionId, status, resultCount, exception.getErrorCode().code());
            throw exception;
        } catch (RuntimeException exception) {
            logResult(regionId, status, resultCount, ErrorCode.INTERNAL_SERVER_ERROR.code());
            throw exception;
        }
    }

    private ContentStatus toContentStatus(String status) {
        if (status == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return switch (status) {
            case "PENDING" -> ContentStatus.PENDING;
            case "APPROVED" -> ContentStatus.APPROVED;
            default -> throw new BusinessException(ErrorCode.INVALID_INPUT);
        };
    }

    private List<RegionAdminContentCandidate> findCandidates(
        Long regionId,
        ContentStatus contentStatus
    ) {
        List<Content> contents = contentService.findContentsByRegionIdAndStatus(regionId, contentStatus);
        return switch (contentStatus) {
            case PENDING -> findPendingCandidates(contents, regionId);
            case APPROVED -> findApprovedCandidates(contents, regionId);
            default -> throw new IllegalStateException("unsupported content status: " + contentStatus);
        };
    }

    private List<RegionAdminContentCandidate> findPendingCandidates(List<Content> contents, Long regionId) {
        return originalContentReviewTargetService.findByContents(contents)
            .stream()
            .filter(OriginalContentReviewTarget::isOriginalReviewTarget)
            .map(target -> validateAndCreateCandidate(
                target.content(),
                target.pendingLog(),
                regionId,
                ContentStatus.PENDING
            ))
            .sorted(Comparator
                .comparing(RegionAdminContentCandidate::statusAt)
                .thenComparing(candidate -> candidate.content().getContentId()))
            .toList();
    }

    private List<RegionAdminContentCandidate> findApprovedCandidates(List<Content> contents, Long regionId) {
        if (contents.isEmpty()) {
            return List.of();
        }
        List<Long> contentIds = contents.stream()
            .map(Content::getContentId)
            .toList();
        Map<Long, ContentLog> approvedLogs = contentLogService.findLatestByContentIdsAndStatus(
            contentIds,
            ContentLogStatus.APPROVED
        );
        return contents.stream()
            .map(content -> validateAndCreateCandidate(
                content,
                approvedLogs.get(content.getContentId()),
                regionId,
                ContentStatus.APPROVED
            ))
            .sorted(Comparator
                .comparing((RegionAdminContentCandidate candidate) -> candidate.content().getPublishAt())
                .thenComparing(candidate -> candidate.content().getContentId()))
            .toList();
    }

    private RegionAdminContentCandidate validateAndCreateCandidate(
        Content content,
        ContentLog statusLog,
        Long regionId,
        ContentStatus expectedStatus
    ) {
        AppUser operator = content.getOperator();
        ImageObject representativeImageObject = content.getRepresentativeImageObject();
        if (content.getContentId() == null
            || content.getDeletedAt() != null
            || content.getStatus() != expectedStatus
            || !content.isScopedTo(regionId)
            || content.getPublishAt() == null
            || statusLog == null
            || statusLog.getStatus() != ContentLogStatus.valueOf(expectedStatus.name())
            || statusLog.getDate() == null
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
        return new RegionAdminContentCandidate(content, statusLog.getDate(), representativeImageObject);
    }

    private RegionAdminContentListResult.Content toResult(
        RegionAdminContentCandidate candidate,
        ContentStatus contentStatus
    ) {
        Content content = candidate.content();
        RepresentativeImageViewUrl imageViewUrl = representativeImageViewUrlService
            .createViewUrl(candidate.representativeImageObject());
        return new RegionAdminContentListResult.Content(
            content.getContentId(),
            content.getContentType(),
            content.getTitle(),
            content.getStatus(),
            content.getPublishAt(),
            contentStatus == ContentStatus.PENDING ? candidate.statusAt() : null,
            contentStatus == ContentStatus.APPROVED ? candidate.statusAt() : null,
            content.getOperator().getUserId(),
            content.getOperator().getName(),
            imageViewUrl.url(),
            imageViewUrl.expiresAt()
        );
    }

    private void logResult(
        Long regionId,
        String status,
        int resultCount,
        String resultCode
    ) {
        log.info(
            "Region admin contents queried. requestId={}, regionId={}, status={}, resultCount={}, resultCode={}",
            RequestIdFilter.currentRequestId(),
            regionId,
            status,
            resultCount,
            resultCode
        );
    }

    private record RegionAdminContentCandidate(
        Content content,
        Instant statusAt,
        ImageObject representativeImageObject
    ) {
    }
}
