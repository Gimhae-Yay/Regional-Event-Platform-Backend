package io.regionevent.regioneventbackend.domain.content.dto;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.service.PublicContentDetailResult;

public record GetPublicContentResponse(
    String contentId,
    ContentType contentType,
    String title,
    String description,
    String representativeImageUrl,
    Instant representativeImageUrlExpiresAt,
    String locationText,
    String operatingHoursText,
    String contactText,
    String precautions,
    String ageRequirement,
    String materials,
    String cancellationPolicyText
) {

    public static GetPublicContentResponse from(PublicContentDetailResult result) {
        return new GetPublicContentResponse(
            result.contentId().toString(),
            result.contentType(),
            result.title(),
            result.description(),
            result.representativeImageUrl(),
            result.representativeImageUrlExpiresAt(),
            result.locationText(),
            result.operatingHoursText(),
            result.contactText(),
            result.precautions(),
            result.ageRequirement(),
            result.materials(),
            result.cancellationPolicyText()
        );
    }
}
