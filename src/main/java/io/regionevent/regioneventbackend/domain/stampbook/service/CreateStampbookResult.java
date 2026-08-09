package io.regionevent.regioneventbackend.domain.stampbook.service;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.stampbook.entity.Stampbook;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookStatus;

public record CreateStampbookResult(
    Long stampbookId,
    StampbookStatus status,
    int targetCount,
    Instant createdAt
) {

    public static CreateStampbookResult from(
        Stampbook stampbook,
        int targetCount,
        Instant createdAt
    ) {
        return new CreateStampbookResult(
            stampbook.getStampbookId(),
            stampbook.getStatus(),
            targetCount,
            createdAt
        );
    }
}
