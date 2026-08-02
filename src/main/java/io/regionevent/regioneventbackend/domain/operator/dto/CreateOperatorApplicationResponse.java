package io.regionevent.regioneventbackend.domain.operator.dto;

public record CreateOperatorApplicationResponse(
    Long operatorApplicationId,
    Long requestedRegionId,
    String status
) {
}
