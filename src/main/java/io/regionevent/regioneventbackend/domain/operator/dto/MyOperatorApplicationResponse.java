package io.regionevent.regioneventbackend.domain.operator.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.regionevent.regioneventbackend.domain.operator.entity.OperatorApplication;
import io.regionevent.regioneventbackend.domain.operator.entity.OperatorApplicationStatus;

public record MyOperatorApplicationResponse(
    @JsonInclude(JsonInclude.Include.ALWAYS) ApplicationResponse operatorApplication
) {

    public static MyOperatorApplicationResponse empty() {
        return new MyOperatorApplicationResponse(null);
    }

    public static MyOperatorApplicationResponse from(OperatorApplication application) {
        return new MyOperatorApplicationResponse(ApplicationResponse.from(application));
    }

    public record ApplicationResponse(
        String operatorApplicationId,
        String status,
        String requestedRegionId,
        String requestedRegionName,
        Instant createdAt,
        @JsonInclude(JsonInclude.Include.ALWAYS) Instant reviewedAt,
        @JsonInclude(JsonInclude.Include.ALWAYS) String rejectedReason
    ) {

        private static ApplicationResponse from(OperatorApplication application) {
            OperatorApplicationStatus status = application.getStatus();
            if (status == OperatorApplicationStatus.CANCELLED) {
                throw new IllegalStateException("linked operator application must not be cancelled");
            }

            Instant reviewedAt = status == OperatorApplicationStatus.PENDING
                ? null
                : application.getUpdatedAt();
            String rejectedReason = status == OperatorApplicationStatus.REJECTED
                ? application.getRejectedReason()
                : null;

            return new ApplicationResponse(
                application.getOperatorApplicationId().toString(),
                status.name(),
                application.getRequestedRegion().getRegionId().toString(),
                application.getRequestedRegion().getName(),
                application.getCreatedAt(),
                reviewedAt,
                rejectedReason
            );
        }
    }
}
