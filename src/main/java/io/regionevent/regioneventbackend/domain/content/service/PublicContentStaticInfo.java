package io.regionevent.regioneventbackend.domain.content.service;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.PublicContentProjection;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

public record PublicContentStaticInfo(
    Long regionId,
    Long contentId,
    int versionNo,
    ContentType contentType,
    String title,
    String description,
    String locationText,
    String operatingHoursText,
    String precautions,
    String ageRequirement,
    String materials,
    String cancellationPolicyText
) {

    public PublicContentStaticInfo {
        if (regionId == null || regionId <= 0) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        if (contentId == null || contentId <= 0) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        if (versionNo < 0) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        if (contentType == null) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        validateText(title);
        validateText(description);
        validateText(locationText);
        validateText(operatingHoursText);
        validateText(precautions);
        validateText(ageRequirement);
        validateText(materials);
        validateText(cancellationPolicyText);
    }

    public static PublicContentStaticInfo from(Content content) {
        return new PublicContentStaticInfo(
            content.getRegion().getRegionId(),
            content.getContentId(),
            content.getVersionNo(),
            content.getContentType(),
            content.getTitle(),
            content.getDescription(),
            content.getLocationText(),
            content.getOperatingHoursText(),
            content.getPrecautions(),
            content.getAgeRequirement(),
            content.getMaterials(),
            content.getCancellationPolicyText()
        );
    }

    public static PublicContentStaticInfo from(PublicContentProjection projection) {
        return new PublicContentStaticInfo(
            projection.regionId(),
            projection.contentId(),
            projection.versionNo(),
            projection.contentType(),
            projection.title(),
            projection.description(),
            projection.locationText(),
            projection.operatingHoursText(),
            projection.precautions(),
            projection.ageRequirement(),
            projection.materials(),
            projection.cancellationPolicyText()
        );
    }

    private static void validateText(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}
