package io.regionevent.regioneventbackend.domain.content.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.SessionRevision;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class GetSessionRevisionReviewDetailUseCase {

    private final RegionAdminAuthorizationService regionAdminAuthorizationService;
    private final SessionRevisionService sessionRevisionService;

    public GetSessionRevisionReviewDetailUseCase(
        RegionAdminAuthorizationService regionAdminAuthorizationService,
        SessionRevisionService sessionRevisionService
    ) {
        this.regionAdminAuthorizationService = regionAdminAuthorizationService;
        this.sessionRevisionService = sessionRevisionService;
    }

    @Transactional(readOnly = true)
    public SessionRevisionReviewDetailResult get(
        Long authenticatedUserId,
        Long revisionId
    ) {
        Long authorizedRegionId = regionAdminAuthorizationService.requireAuthorizedRegionId(authenticatedUserId);
        SessionRevision revision = sessionRevisionService.findPendingReviewDetailById(revisionId);
        validateRegion(revision, authorizedRegionId);
        return toResult(revision);
    }

    private void validateRegion(
        SessionRevision revision,
        Long authorizedRegionId
    ) {
        Region revisionRegion = revision.getRegion();
        Content content = revision.getContent();
        ContentSession targetSession = revision.getTargetSession();
        AppUser requestedBy = revision.getRequestedBy();
        if (revisionRegion == null
            || content == null
            || targetSession == null
            || requestedBy == null
            || revisionRegion.getRegionId() == null
            || content.getContentId() == null
            || content.getRegion() == null
            || content.getRegion().getRegionId() == null
            || targetSession.getSessionId() == null
            || targetSession.getContent() == null
            || targetSession.getContent().getContentId() == null
            || targetSession.getRegion() == null
            || targetSession.getRegion().getRegionId() == null
            || requestedBy.getUserId() == null
            || requestedBy.getName() == null
            || !revisionRegion.getRegionId().equals(content.getRegion().getRegionId())
            || !content.getContentId().equals(targetSession.getContent().getContentId())
            || !revisionRegion.getRegionId().equals(targetSession.getRegion().getRegionId())) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        if (!authorizedRegionId.equals(revisionRegion.getRegionId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private SessionRevisionReviewDetailResult toResult(SessionRevision revision) {
        Content content = revision.getContent();
        ContentSession targetSession = revision.getTargetSession();
        AppUser requestedBy = revision.getRequestedBy();
        return new SessionRevisionReviewDetailResult(
            revision.getSessionRevisionId(),
            content.getContentId(),
            content.getTitle(),
            content.getStatus(),
            new SessionRevisionReviewDetailResult.TargetSession(
                targetSession.getSessionId(),
                targetSession.getStatus(),
                targetSession.getVersionNo(),
                targetSession.getStartsAt(),
                targetSession.getEndsAt(),
                targetSession.getCheckinOpenAt(),
                targetSession.getCheckinCloseAt(),
                targetSession.getCapacity(),
                targetSession.getRemainingCapacity()
            ),
            revision.getBaseSessionVersion(),
            new SessionRevisionReviewDetailResult.Candidate(
                revision.getStartsAt(),
                revision.getEndsAt(),
                revision.getCheckinOpenAt(),
                revision.getCheckinCloseAt(),
                revision.getCapacity()
            ),
            revision.getSubmittedAt(),
            new SessionRevisionReviewDetailResult.Operator(
                requestedBy.getUserId(),
                requestedBy.getName()
            )
        );
    }
}
