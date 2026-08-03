package io.regionevent.regioneventbackend.domain.content.service;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;

public record EndContentReservationsResult(
    Long contentId,
    ContentStatus status,
    Instant endedAt
) {

    public static EndContentReservationsResult from(Content content, Instant endedAt) {
        return new EndContentReservationsResult(
            content.getContentId(),
            content.getStatus(),
            endedAt
        );
    }
}
