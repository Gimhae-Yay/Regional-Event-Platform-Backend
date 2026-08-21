package io.regionevent.regioneventbackend.domain.content.dto;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import io.regionevent.regioneventbackend.domain.content.service.LatestContentRevisionDetailResult;

public record GetLatestContentRevisionResponse(
    String revisionId,
    String contentId,
    int revisionNo,
    int baseContentVersion,
    String status,
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
    long reservationPrice,
    OffsetDateTime publishAt,
    String reviewReason,
    Instant submittedAt,
    Instant reviewedAt
) {

    private static final ZoneId SEOUL_TIME_ZONE = ZoneId.of("Asia/Seoul");

    public static GetLatestContentRevisionResponse from(LatestContentRevisionDetailResult result) {
        return new GetLatestContentRevisionResponse(
            result.revisionId().toString(),
            result.contentId().toString(),
            result.revisionNo(),
            result.baseContentVersion(),
            result.status().name(),
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
            result.reservationPrice(),
            toSeoulOffsetDateTime(result.publishAt()),
            result.reviewReason(),
            result.submittedAt(),
            result.reviewedAt()
        );
    }

    private static OffsetDateTime toSeoulOffsetDateTime(Instant instant) {
        return instant == null ? null : instant.atZone(SEOUL_TIME_ZONE).toOffsetDateTime();
    }
}
