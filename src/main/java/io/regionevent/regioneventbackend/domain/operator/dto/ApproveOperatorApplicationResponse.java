package io.regionevent.regioneventbackend.domain.operator.dto;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.operator.entity.OperatorApplication;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;

public record ApproveOperatorApplicationResponse(
    Long operatorApplicationId,
    String status,
    String operatorRole,
    Long assignedRegionId,
    Instant processedAt
) {

    public static ApproveOperatorApplicationResponse from(OperatorApplication application) {
        return new ApproveOperatorApplicationResponse(
            application.getOperatorApplicationId(),
            application.getStatus().name(),
            UserRole.OPERATOR.name(),
            application.getRequestedRegion().getRegionId(),
            application.getUpdatedAt()
        );
    }
}
