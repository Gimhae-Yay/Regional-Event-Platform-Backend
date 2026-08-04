package io.regionevent.regioneventbackend.domain.content.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.SessionRevision;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class GetPendingSessionRevisionsUseCase {

    private static final String PENDING_STATUS = "PENDING";

    private final RegionAdminAuthorizationService regionAdminAuthorizationService;
    private final SessionRevisionService sessionRevisionService;

    public GetPendingSessionRevisionsUseCase(
        RegionAdminAuthorizationService regionAdminAuthorizationService,
        SessionRevisionService sessionRevisionService
    ) {
        this.regionAdminAuthorizationService = regionAdminAuthorizationService;
        this.sessionRevisionService = sessionRevisionService;
    }

    @Transactional(readOnly = true)
    public PendingSessionRevisionListResult get(Long authenticatedUserId, String status) {
        Long regionId = regionAdminAuthorizationService.requireAuthorizedRegionId(authenticatedUserId);
        validateStatus(status);
        List<PendingSessionRevisionListResult.Revision> revisions = sessionRevisionService
            .findPendingByRegionId(regionId)
            .stream()
            .map(this::toResult)
            .toList();
        return new PendingSessionRevisionListResult(revisions);
    }

    private void validateStatus(String status) {
        if (!PENDING_STATUS.equals(status)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private PendingSessionRevisionListResult.Revision toResult(SessionRevision revision) {
        ContentSession targetSession = revision.getTargetSession();
        AppUser operator = revision.getRequestedBy();
        if (targetSession.getStatus() != ContentSessionStatus.SCHEDULED
            || !targetSession.getContent().getContentId().equals(revision.getContent().getContentId())
            || !targetSession.getRegion().getRegionId().equals(revision.getRegion().getRegionId())
            || operator.getUserId() == null
            || operator.getName() == null) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        return new PendingSessionRevisionListResult.Revision(
            revision.getSessionRevisionId(),
            revision.getContent().getContentId(),
            revision.getContent().getTitle(),
            targetSession.getSessionId(),
            revision.getBaseSessionVersion(),
            revision.getStartsAt(),
            revision.getEndsAt(),
            revision.getCheckinOpenAt(),
            revision.getCheckinCloseAt(),
            revision.getCapacity(),
            revision.getSubmittedAt(),
            operator.getUserId(),
            operator.getName()
        );
    }
}
