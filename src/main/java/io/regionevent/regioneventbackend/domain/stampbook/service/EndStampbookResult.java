package io.regionevent.regioneventbackend.domain.stampbook.service;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.stampbook.entity.Stampbook;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookStatus;

public record EndStampbookResult(
    Long stampbookId,
    StampbookStatus status,
    Instant endedAt
) {

    public static EndStampbookResult from(
        Stampbook stampbook,
        Instant endedAt
    ) {
        return new EndStampbookResult(
            stampbook.getStampbookId(),
            stampbook.getStatus(),
            endedAt
        );
    }
}
