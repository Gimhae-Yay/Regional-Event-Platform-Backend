package io.regionevent.regioneventbackend.domain.stampbook.service;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.stampbook.entity.Stampbook;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookStatus;

public record RequestStampbookPublicationResult(
    Long stampbookId,
    StampbookStatus status,
    Instant requestedAt
) {

    public static RequestStampbookPublicationResult from(
        Stampbook stampbook,
        Instant requestedAt
    ) {
        return new RequestStampbookPublicationResult(
            stampbook.getStampbookId(),
            stampbook.getStatus(),
            requestedAt
        );
    }
}
