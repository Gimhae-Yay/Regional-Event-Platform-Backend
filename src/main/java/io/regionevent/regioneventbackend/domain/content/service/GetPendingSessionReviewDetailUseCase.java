package io.regionevent.regioneventbackend.domain.content.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.repository.ContentSessionRepository;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class GetPendingSessionReviewDetailUseCase {

    private final RegionAdminAuthorizationService regionAdminAuthorizationService;
    private final ContentSessionRepository contentSessionRepository;

    public GetPendingSessionReviewDetailUseCase(
        RegionAdminAuthorizationService regionAdminAuthorizationService,
        ContentSessionRepository contentSessionRepository
    ) {
        this.regionAdminAuthorizationService = regionAdminAuthorizationService;
        this.contentSessionRepository = contentSessionRepository;
    }

    @Transactional(readOnly = true)
    public PendingSessionReviewDetailResult get(Long authenticatedUserId, Long sessionId) {
        Long regionId = regionAdminAuthorizationService.requireAuthorizedRegionId(authenticatedUserId);
        ContentSession session = contentSessionRepository.findPendingReviewTarget(
            sessionId,
            List.of(ContentStatus.APPROVED, ContentStatus.PUBLISHED)
        ).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!session.getRegion().getRegionId().equals(regionId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return PendingSessionReviewDetailResult.from(session);
    }
}
