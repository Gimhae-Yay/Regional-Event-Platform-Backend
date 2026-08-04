package io.regionevent.regioneventbackend.domain.content.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class GetPendingContentSessionsUseCase {

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
        Long regionId = regionAdminAuthorizationService.requireAuthorizedRegionId(authenticatedUserId);
        if (!PENDING_STATUS.equals(status)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        List<PendingContentSessionListResult.Session> sessions = contentSessionService
            .findPendingReviewCandidatesByRegionId(regionId)
            .stream()
            .map(contentSession -> toResult(contentSession, regionId))
            .toList();
        return new PendingContentSessionListResult(sessions);
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
