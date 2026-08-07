package io.regionevent.regioneventbackend.domain.content.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.content.repository.PublicContentListVerificationProjection;
import io.regionevent.regioneventbackend.domain.image.entity.ImageObject;
import io.regionevent.regioneventbackend.domain.image.service.RepresentativeImageViewUrl;
import io.regionevent.regioneventbackend.domain.image.service.RepresentativeImageViewUrlService;
import io.regionevent.regioneventbackend.domain.region.service.RegionService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class GetPublicContentsUseCase {

    private final RegionService regionService;
    private final ContentService contentService;
    private final PublicContentCacheAside publicContentCacheAside;
    private final RepresentativeImageViewUrlService representativeImageViewUrlService;

    public GetPublicContentsUseCase(
        RegionService regionService,
        ContentService contentService,
        PublicContentCacheAside publicContentCacheAside,
        RepresentativeImageViewUrlService representativeImageViewUrlService
    ) {
        this.regionService = regionService;
        this.contentService = contentService;
        this.publicContentCacheAside = publicContentCacheAside;
        this.representativeImageViewUrlService = representativeImageViewUrlService;
    }

    @Transactional(readOnly = true)
    public PublicContentListResult get(PublicContentSearchCondition condition) {
        regionService.findPublicRegion(condition.regionId());
        List<PublicContentListResult.Content> contents = contentService.findPublicContentListVerifications(
            condition.regionId(),
            condition.contentType(),
            condition.reservationAvailable()
        ).stream()
            .map(this::toResult)
            .toList();
        return new PublicContentListResult(contents);
    }

    private PublicContentListResult.Content toResult(
        PublicContentListVerificationProjection projection
    ) {
        PublicContentStaticInfo staticInfo = publicContentCacheAside.resolveContent(
            projection.regionId(),
            projection.contentId(),
            projection.versionNo(),
            () -> contentService.findPublicContentStaticInfo(
                projection.regionId(),
                projection.contentId(),
                projection.versionNo()
            )
        );
        ImageObject representativeImageObject = projection.representativeImageObject();
        if (representativeImageObject == null
            || projection.representativeImageAssignedAt() == null
            || !representativeImageObject.isScopedTo(projection.regionId())) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        RepresentativeImageViewUrl representativeImageViewUrl =
            representativeImageViewUrlService.createViewUrl(representativeImageObject);
        return new PublicContentListResult.Content(
            staticInfo.contentId(),
            staticInfo.contentType(),
            staticInfo.title(),
            staticInfo.locationText(),
            representativeImageViewUrl.url(),
            representativeImageViewUrl.expiresAt(),
            projection.reservationAvailable()
        );
    }
}
