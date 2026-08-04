package io.regionevent.regioneventbackend.domain.content.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.image.entity.ImageObject;
import io.regionevent.regioneventbackend.domain.image.service.RepresentativeImageViewUrl;
import io.regionevent.regioneventbackend.domain.image.service.RepresentativeImageViewUrlService;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class GetOriginalContentReviewDetailUseCase {

    private static final Logger log = LoggerFactory.getLogger(GetOriginalContentReviewDetailUseCase.class);

    private final RegionAdminAuthorizationService regionAdminAuthorizationService;
    private final OriginalContentReviewTargetService originalContentReviewTargetService;
    private final ContentSessionService contentSessionService;
    private final RepresentativeImageViewUrlService representativeImageViewUrlService;

    public GetOriginalContentReviewDetailUseCase(
        RegionAdminAuthorizationService regionAdminAuthorizationService,
        OriginalContentReviewTargetService originalContentReviewTargetService,
        ContentSessionService contentSessionService,
        RepresentativeImageViewUrlService representativeImageViewUrlService
    ) {
        this.regionAdminAuthorizationService = regionAdminAuthorizationService;
        this.originalContentReviewTargetService = originalContentReviewTargetService;
        this.contentSessionService = contentSessionService;
        this.representativeImageViewUrlService = representativeImageViewUrlService;
    }

    @Transactional(readOnly = true)
    public OriginalContentReviewDetailResult get(Long authenticatedUserId, Long contentId) {
        Long regionId = null;
        try {
            regionId = regionAdminAuthorizationService.requireAuthorizedRegionId(authenticatedUserId);
            OriginalContentReviewTarget target = originalContentReviewTargetService.findByContentId(contentId)
                .filter(OriginalContentReviewTarget::isOriginalReviewTarget)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
            Content content = target.content();
            validateRegion(content, regionId);
            ImageObject representativeImageObject = validateRepresentativeImage(content, regionId);

            List<ContentSession> sessions = contentSessionService.findPendingByContentId(contentId);
            if (sessions.isEmpty()) {
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
            }

            RepresentativeImageViewUrl imageViewUrl = representativeImageViewUrlService
                .createViewUrl(representativeImageObject);
            OriginalContentReviewDetailResult result = toResult(content, imageViewUrl, sessions);
            logResult(regionId, contentId, "SUCCESS");
            return result;
        } catch (BusinessException exception) {
            logResult(regionId, contentId, exception.getErrorCode().code());
            throw exception;
        } catch (RuntimeException exception) {
            logResult(regionId, contentId, ErrorCode.INTERNAL_SERVER_ERROR.code());
            throw exception;
        }
    }

    private void validateRegion(Content content, Long authorizedRegionId) {
        if (!content.getRegion().getRegionId().equals(authorizedRegionId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private ImageObject validateRepresentativeImage(Content content, Long regionId) {
        ImageObject representativeImageObject = content.getRepresentativeImageObject();
        if (content.getRepresentativeImageAssignedAt() == null
            || representativeImageObject == null
            || !representativeImageObject.isScopedTo(regionId)) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        return representativeImageObject;
    }

    private OriginalContentReviewDetailResult toResult(
        Content content,
        RepresentativeImageViewUrl imageViewUrl,
        List<ContentSession> sessions
    ) {
        return new OriginalContentReviewDetailResult(
            content.getContentId(),
            content.getRegion().getRegionId(),
            content.getOperator().getUserId(),
            content.getContentType(),
            content.getStatus(),
            content.getTitle(),
            content.getDescription(),
            imageViewUrl.url(),
            imageViewUrl.expiresAt(),
            content.getLocationText(),
            content.getOperatingHoursText(),
            content.getContactText(),
            content.getPrecautions(),
            content.getAgeRequirement(),
            content.getMaterials(),
            content.getCancellationPolicyText(),
            content.getPublishAt(),
            sessions.stream()
                .map(this::toSession)
                .toList()
        );
    }

    private OriginalContentReviewDetailResult.Session toSession(ContentSession session) {
        return new OriginalContentReviewDetailResult.Session(
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

    private void logResult(Long regionId, Long contentId, String resultCode) {
        log.info(
            "Original content review detail queried. requestId={}, regionId={}, contentId={}, resultCode={}",
            RequestIdFilter.currentRequestId(),
            regionId,
            contentId,
            resultCode
        );
    }
}
