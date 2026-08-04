package io.regionevent.regioneventbackend.domain.content.dto;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.content.service.MyContentDetailResult;

public record GetMyContentResponse(
    String contentId,
    ContentType contentType,
    ContentStatus status,
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
    String cancellationPolicyText,
    OffsetDateTime publishAt,
    String rejectionReason,
    Instant createdAt,
    Instant updatedAt
) {

    private static final ZoneId SEOUL_TIME_ZONE = ZoneId.of("Asia/Seoul");

    public static GetMyContentResponse from(MyContentDetailResult result) {
        return new GetMyContentResponse(
            result.contentId().toString(),
            result.contentType(),
            result.status(),
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
            result.cancellationPolicyText(),
            toSeoulOffsetDateTime(result.publishAt()),
            result.rejectionReason(),
            result.createdAt(),
            result.updatedAt()
        );
    }

    private static OffsetDateTime toSeoulOffsetDateTime(Instant instant) {
        return instant.atZone(SEOUL_TIME_ZONE).toOffsetDateTime();
    }
}
