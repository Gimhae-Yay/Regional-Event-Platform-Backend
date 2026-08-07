package io.regionevent.regioneventbackend.domain.operator.dto;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import io.regionevent.regioneventbackend.domain.operator.entity.OperatorApplication;

public record PendingOperatorApplicationsResponse(
    List<OperatorRequest> operatorRequests
) {

    private static final ZoneId SEOUL_TIME_ZONE = ZoneId.of("Asia/Seoul");

    public PendingOperatorApplicationsResponse {
        operatorRequests = List.copyOf(operatorRequests);
    }

    public static PendingOperatorApplicationsResponse from(List<OperatorApplication> applications) {
        return new PendingOperatorApplicationsResponse(
            applications.stream()
                .map(OperatorRequest::from)
                .toList()
        );
    }

    public record OperatorRequest(
        Long operatorApplicationId,
        Long applicantUserId,
        Long requestedRegionId,
        OffsetDateTime requestedAt
    ) {

        private static OperatorRequest from(OperatorApplication application) {
            return new OperatorRequest(
                application.getOperatorApplicationId(),
                application.getApplicant().getUserId(),
                application.getRequestedRegion().getRegionId(),
                application.getCreatedAt().atZone(SEOUL_TIME_ZONE).toOffsetDateTime()
            );
        }
    }
}
