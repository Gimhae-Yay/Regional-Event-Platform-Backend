package io.regionevent.regioneventbackend.domain.content.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class GetPendingContentSessionsUseCase {

    private static final Logger log = LoggerFactory.getLogger(GetPendingContentSessionsUseCase.class);
    private static final String PENDING_STATUS = "PENDING";

    private final RegionAdminAuthorizationService regionAdminAuthorizationService;
    private final ContentSessionService contentSessionService;

    public GetPendingContentSessionsUseCase(
        RegionAdminAuthorizationService regionAdminAuthorizationService,
        ContentSessionService contentSessionService
    ) {
        this.regionAdminAuthorizationService = regionAdminAuthorizationService;
        this.contentSessionService = contentSessionService;
    }

    @Transactional(readOnly = true)
    public PendingContentSessionListResult get(Long authenticatedUserId, String status) {
        Long regionId = null;
        int resultCount = 0;
        try {
            regionId = regionAdminAuthorizationService.requireAuthorizedRegionId(authenticatedUserId);
            validateStatus(status);
            Long authorizedRegionId = regionId;
            List<PendingContentSessionListResult.Session> sessions = contentSessionService
                .findPendingReviewCandidatesByRegionId(regionId)
                .stream()
                .map(contentSession -> toResult(contentSession, authorizedRegionId))
                .toList();
            resultCount = sessions.size();
            logResult(regionId, resultCount, "SUCCESS");
            return new PendingContentSessionListResult(sessions);
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

    private void logResult(Long regionId, int resultCount, String resultCode) {
        log.info(
            "Pending content sessions queried. requestId={}, regionId={}, resultCount={}, resultCode={}",
            RequestIdFilter.currentRequestId(),
            regionId,
            resultCount,
            resultCode
        );
    }

    private PendingContentSessionListResult.Session toResult(
        ContentSession contentSession,
        Long regionId
    ) {
        Content content = contentSession.getContent();
        AppUser operator = content == null ? null : content.getOperator();
        if (content == null
            || content.getContentId() == null
            || content.getRegion() == null
            || !regionId.equals(content.getRegion().getRegionId())
            || operator == null
            || operator.getUserId() == null
            || operator.getName() == null) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        return new PendingContentSessionListResult.Session(
            contentSession.getSessionId(),
            content.getContentId(),
            content.getTitle(),
            contentSession.getStatus().name(),
            contentSession.getStartsAt(),
            contentSession.getEndsAt(),
            contentSession.getCheckinOpenAt(),
            contentSession.getCheckinCloseAt(),
            contentSession.getCapacity(),
            contentSession.getCreatedAt(),
            operator.getUserId(),
            operator.getName()
        );
    }
}
