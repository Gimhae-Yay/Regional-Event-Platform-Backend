package io.regionevent.regioneventbackend.domain.content.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService;

@Service
public class GetContentHistoryUseCase {

    private final ContentHistoryTargetService contentHistoryTargetService;
    private final RegionAdminAuthorizationService regionAdminAuthorizationService;
    private final ContentHistoryService contentHistoryService;

    public GetContentHistoryUseCase(
        ContentHistoryTargetService contentHistoryTargetService,
        RegionAdminAuthorizationService regionAdminAuthorizationService,
        ContentHistoryService contentHistoryService
    ) {
        this.contentHistoryTargetService = contentHistoryTargetService;
        this.regionAdminAuthorizationService = regionAdminAuthorizationService;
        this.contentHistoryService = contentHistoryService;
    }

    @Transactional(readOnly = true)
    public ContentHistoryResult get(Long userId, Long contentId) {
        ContentHistoryTarget target = contentHistoryTargetService.findById(contentId);
        regionAdminAuthorizationService.authorize(userId, target.regionId());
        return contentHistoryService.findAllByContentId(target.contentId());
    }
}
