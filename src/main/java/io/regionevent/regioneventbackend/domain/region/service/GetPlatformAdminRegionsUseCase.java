package io.regionevent.regioneventbackend.domain.region.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.user.service.PlatformAdminAuthorizationService;
import io.regionevent.regioneventbackend.global.config.RequestIdFilter;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class GetPlatformAdminRegionsUseCase {

    private static final Logger log = LoggerFactory.getLogger(GetPlatformAdminRegionsUseCase.class);

    private final PlatformAdminAuthorizationService platformAdminAuthorizationService;
    private final RegionService regionService;

    public GetPlatformAdminRegionsUseCase(
        PlatformAdminAuthorizationService platformAdminAuthorizationService,
        RegionService regionService
    ) {
        this.platformAdminAuthorizationService = platformAdminAuthorizationService;
        this.regionService = regionService;
    }

    @Transactional(readOnly = true)
    public List<PlatformAdminRegionListInfo> get(
        Long actorUserId,
        Boolean isPublic
    ) {
        int resultCount = 0;
        try {
            platformAdminAuthorizationService.requireAuthorizedPlatformAdmin(actorUserId);
            List<PlatformAdminRegionListInfo> regions = regionService.findPlatformAdminRegionList(isPublic).stream()
                .map(PlatformAdminRegionListInfo::from)
                .toList();
            resultCount = regions.size();
            logResult(resultCount, "SUCCESS");
            return regions;
        } catch (BusinessException exception) {
            logResult(resultCount, exception.getErrorCode().code());
            throw exception;
        } catch (RuntimeException exception) {
            logResult(resultCount, ErrorCode.INTERNAL_SERVER_ERROR.code());
            throw exception;
        }
    }

    private void logResult(int resultCount, String resultCode) {
        log.info(
            "Platform admin region list queried. requestId={}, resultCount={}, resultCode={}",
            RequestIdFilter.currentRequestId(),
            resultCount,
            resultCode
        );
    }
}
