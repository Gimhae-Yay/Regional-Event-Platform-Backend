package io.regionevent.regioneventbackend.domain.content.service;

import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.repository.PublicContentStaticProjection;

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
            throw new IllegalArgumentException("regionId must be positive");
        }
        if (contentId == null || contentId <= 0) {
            throw new IllegalArgumentException("contentId must be positive");
        }
        if (versionNo < 0) {
            throw new IllegalArgumentException("versionNo must not be negative");
        }
        if (contentType == null) {
            throw new IllegalArgumentException("contentType must not be null");
        }
        validateText(title, "title");
        validateText(description, "description");
        validateText(locationText, "locationText");
        validateText(operatingHoursText, "operatingHoursText");
        validateText(precautions, "precautions");
        validateText(ageRequirement, "ageRequirement");
        validateText(materials, "materials");
        validateText(cancellationPolicyText, "cancellationPolicyText");
    }

    public static PublicContentStaticInfo from(PublicContentStaticProjection projection) {
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

    private static void validateText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}
