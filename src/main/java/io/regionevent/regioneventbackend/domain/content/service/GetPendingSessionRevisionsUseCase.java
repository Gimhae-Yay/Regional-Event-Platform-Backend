package io.regionevent.regioneventbackend.domain.content.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.SessionRevision;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class GetPendingSessionRevisionsUseCase {

    private static final Logger log = LoggerFactory.getLogger(GetPendingSessionRevisionsUseCase.class);
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
        Long regionId = null;
        int resultCount = 0;
        try {
            regionId = regionAdminAuthorizationService.requireAuthorizedRegionId(authenticatedUserId);
            validateStatus(status);
            List<PendingSessionRevisionListResult.Revision> revisions = sessionRevisionService
                .findPendingByRegionId(regionId)
                .stream()
                .map(this::toResult)
                .toList();
            resultCount = revisions.size();
            logResult(regionId, resultCount, "SUCCESS");
            return new PendingSessionRevisionListResult(revisions);
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

    private PendingSessionRevisionListResult.Revision toResult(SessionRevision revision) {
        ContentSession targetSession = revision.getTargetSession();
        AppUser operator = revision.getRequestedBy();
        if (!targetSession.getContent().getContentId().equals(revision.getContent().getContentId())
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

    private void logResult(Long regionId, int resultCount, String resultCode) {
        log.info(
            "Pending session revisions queried. requestId={}, regionId={}, resultCount={}, resultCode={}",
            RequestIdFilter.currentRequestId(),
            regionId,
            resultCount,
            resultCode
        );
    }
}
