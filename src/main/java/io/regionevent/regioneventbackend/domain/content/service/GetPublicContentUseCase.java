package io.regionevent.regioneventbackend.domain.content.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.image.entity.ImageObject;
import io.regionevent.regioneventbackend.domain.image.service.RepresentativeImageViewUrl;
import io.regionevent.regioneventbackend.domain.image.service.RepresentativeImageViewUrlService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class GetPublicContentUseCase {

    private final ContentService contentService;
    private final PublicCatalogCacheAside publicCatalogCacheAside;
    private final RepresentativeImageViewUrlService representativeImageViewUrlService;

    public GetPublicContentUseCase(
        ContentService contentService,
        PublicCatalogCacheAside publicCatalogCacheAside,
        RepresentativeImageViewUrlService representativeImageViewUrlService
    ) {
        this.contentService = contentService;
        this.publicCatalogCacheAside = publicCatalogCacheAside;
        this.representativeImageViewUrlService = representativeImageViewUrlService;
    }

    @Transactional(readOnly = true)
    public PublicContentDetailResult get(Long contentId) {
        Content content = contentService.findPublicContent(contentId);
        PublicContentStaticInfo staticInfo = publicCatalogCacheAside.resolveContent(
            PublicContentStaticInfo.from(content)
        );
        RepresentativeImageViewUrl representativeImageViewUrl = createRepresentativeImageViewUrl(content);
        return new PublicContentDetailResult(
            staticInfo.contentId(),
            staticInfo.contentType(),
            staticInfo.title(),
            staticInfo.description(),
            representativeImageViewUrl.url(),
            representativeImageViewUrl.expiresAt(),
            staticInfo.locationText(),
            staticInfo.operatingHoursText(),
            content.getContactText(),
            staticInfo.precautions(),
            staticInfo.ageRequirement(),
            staticInfo.materials(),
            staticInfo.cancellationPolicyText()
        );
    }

    private RepresentativeImageViewUrl createRepresentativeImageViewUrl(Content content) {
        ImageObject representativeImageObject = content.getRepresentativeImageObject();
        if (representativeImageObject == null || content.getRepresentativeImageAssignedAt() == null) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        return representativeImageViewUrlService.createViewUrl(representativeImageObject);
    }
}
