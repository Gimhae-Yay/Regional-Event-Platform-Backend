package io.regionevent.regioneventbackend.domain.operator.dto;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.operator.entity.OperatorApplication;

public record RejectOperatorApplicationResponse(
    Long operatorApplicationId,
    String status,
    String rejectedReason,
    Instant processedAt
) {

    public static RejectOperatorApplicationResponse from(OperatorApplication application) {
        return new RejectOperatorApplicationResponse(
            application.getOperatorApplicationId(),
            application.getStatus().name(),
            application.getRejectedReason(),
            application.getUpdatedAt()
        );
    }
}
