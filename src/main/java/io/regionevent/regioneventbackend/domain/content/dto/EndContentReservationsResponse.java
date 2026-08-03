package io.regionevent.regioneventbackend.domain.content.dto;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.content.service.EndContentReservationsResult;

public record EndContentReservationsResponse(
    String contentId,
    String status,
    Instant endedAt
) {

    public static EndContentReservationsResponse from(EndContentReservationsResult result) {
        return new EndContentReservationsResponse(
            result.contentId().toString(),
            result.status().name(),
            result.endedAt()
        );
    }
}
