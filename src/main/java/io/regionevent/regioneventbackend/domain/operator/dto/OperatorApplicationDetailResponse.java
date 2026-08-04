package io.regionevent.regioneventbackend.domain.operator.dto;

import java.time.OffsetDateTime;
import java.time.ZoneId;

import io.regionevent.regioneventbackend.domain.operator.entity.OperatorApplication;

public record OperatorApplicationDetailResponse(
    Long operatorApplicationId,
    Long applicantUserId,
    Long requestedRegionId,
    String businessInformation,
    String status,
    Long inspectedUserId,
    String rejectedReason,
    OffsetDateTime requestedAt,
    OffsetDateTime updatedAt
) {

    private static final ZoneId SEOUL_TIME_ZONE = ZoneId.of("Asia/Seoul");

    public static OperatorApplicationDetailResponse from(OperatorApplication application) {
        return new OperatorApplicationDetailResponse(
            application.getOperatorApplicationId(),
            application.getApplicant() == null ? null : application.getApplicant().getUserId(),
            application.getRequestedRegion().getRegionId(),
            application.getBusinessInformation(),
            application.getStatus().name(),
            application.getInspectedUser() == null ? null : application.getInspectedUser().getUserId(),
            application.getRejectedReason(),
            application.getCreatedAt().atZone(SEOUL_TIME_ZONE).toOffsetDateTime(),
            application.getUpdatedAt().atZone(SEOUL_TIME_ZONE).toOffsetDateTime()
        );
    }
}
