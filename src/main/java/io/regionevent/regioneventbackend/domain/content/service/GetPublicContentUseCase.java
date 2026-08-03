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
    private final RepresentativeImageViewUrlService representativeImageViewUrlService;

    public GetPublicContentUseCase(
        ContentService contentService,
        RepresentativeImageViewUrlService representativeImageViewUrlService
    ) {
        this.contentService = contentService;
        this.representativeImageViewUrlService = representativeImageViewUrlService;
    }

    @Transactional(readOnly = true)
    public PublicContentDetailResult get(Long contentId) {
        Content content = contentService.findPublicContent(contentId);
        RepresentativeImageViewUrl representativeImageViewUrl = createRepresentativeImageViewUrl(content);
        return new PublicContentDetailResult(
            content.getContentId(),
            content.getContentType(),
            content.getTitle(),
            content.getDescription(),
            representativeImageViewUrl.url(),
            representativeImageViewUrl.expiresAt(),
            content.getLocationText(),
            content.getOperatingHoursText(),
            content.getContactText(),
            content.getPrecautions(),
            content.getAgeRequirement(),
            content.getMaterials(),
            content.getCancellationPolicyText()
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
