package io.regionevent.regioneventbackend.domain.content.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.content.repository.PublicContentProjection;
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
    private final RepresentativeImageViewUrlService representativeImageViewUrlService;

    public GetPublicContentsUseCase(
        RegionService regionService,
        ContentService contentService,
        RepresentativeImageViewUrlService representativeImageViewUrlService
    ) {
        this.regionService = regionService;
        this.contentService = contentService;
        this.representativeImageViewUrlService = representativeImageViewUrlService;
    }

    @Transactional(readOnly = true)
    public PublicContentListResult get(PublicContentSearchCondition condition) {
        regionService.findPublicRegion(condition.regionId());
        List<PublicContentListResult.Content> contents = contentService.findPublicContents(
            condition.regionId(),
            condition.contentType(),
            condition.reservationAvailable()
        ).stream()
            .map(projection -> toResult(projection, condition.regionId()))
            .toList();
        return new PublicContentListResult(contents);
    }

    private PublicContentListResult.Content toResult(
        PublicContentProjection projection,
        Long regionId
    ) {
        ImageObject representativeImageObject = projection.representativeImageObject();
        if (representativeImageObject == null
            || projection.representativeImageAssignedAt() == null
            || !representativeImageObject.isScopedTo(regionId)) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        RepresentativeImageViewUrl representativeImageViewUrl =
            representativeImageViewUrlService.createViewUrl(representativeImageObject);
        return new PublicContentListResult.Content(
            projection.contentId(),
            projection.contentType(),
            projection.title(),
            projection.locationText(),
            representativeImageViewUrl.url(),
            representativeImageViewUrl.expiresAt(),
            projection.reservationAvailable()
        );
    }
}
