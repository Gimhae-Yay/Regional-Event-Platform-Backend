package io.regionevent.regioneventbackend.domain.stampbook.service;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.stampbook.entity.Stampbook;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookStatus;

public record UpdateStampbookResult(
    Long stampbookId,
    StampbookStatus status,
    int targetCount,
    Instant updatedAt
) {

    public static UpdateStampbookResult from(
        Stampbook stampbook,
        int targetCount,
        Instant updatedAt
    ) {
        return new UpdateStampbookResult(
            stampbook.getStampbookId(),
            stampbook.getStatus(),
            targetCount,
            updatedAt
        );
    }
}
