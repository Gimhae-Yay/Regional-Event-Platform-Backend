package io.regionevent.regioneventbackend.domain.stampbook.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService;

@Service
public class GetPendingRegionAdminStampbooksUseCase {

    private final RegionAdminAuthorizationService regionAdminAuthorizationService;
    private final StampbookReadService stampbookReadService;

    public GetPendingRegionAdminStampbooksUseCase(
        RegionAdminAuthorizationService regionAdminAuthorizationService,
        StampbookReadService stampbookReadService
    ) {
        this.regionAdminAuthorizationService = regionAdminAuthorizationService;
        this.stampbookReadService = stampbookReadService;
    }

    @Transactional(readOnly = true)
    public List<PendingRegionAdminStampbookResult> get(Long userId) {
        Long regionId = regionAdminAuthorizationService.requireAuthorizedRegionId(userId);
        return stampbookReadService.findPendingRegionAdminStampbooks(regionId);
    }
}
