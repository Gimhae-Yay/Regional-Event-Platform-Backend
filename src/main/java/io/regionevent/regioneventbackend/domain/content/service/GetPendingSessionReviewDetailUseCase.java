package io.regionevent.regioneventbackend.domain.content.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class GetPendingSessionReviewDetailUseCase {

    private final RegionAdminAuthorizationService regionAdminAuthorizationService;
    private final ContentSessionService contentSessionService;

    public GetPendingSessionReviewDetailUseCase(
        RegionAdminAuthorizationService regionAdminAuthorizationService,
        ContentSessionService contentSessionService
    ) {
        this.regionAdminAuthorizationService = regionAdminAuthorizationService;
        this.contentSessionService = contentSessionService;
    }

    @Transactional(readOnly = true)
    public PendingSessionReviewDetailResult get(Long authenticatedUserId, Long sessionId) {
        Long regionId = regionAdminAuthorizationService.requireAuthorizedRegionId(authenticatedUserId);
        ContentSession session = contentSessionService.findPendingReviewTarget(sessionId);
        if (!session.getRegion().getRegionId().equals(regionId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return PendingSessionReviewDetailResult.from(session);
    }
}
