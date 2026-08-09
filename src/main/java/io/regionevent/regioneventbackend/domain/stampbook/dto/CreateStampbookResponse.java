package io.regionevent.regioneventbackend.domain.stampbook.dto;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookStatus;
import io.regionevent.regioneventbackend.domain.stampbook.service.CreateStampbookResult;

public record CreateStampbookResponse(
    String stampbookId,
    StampbookStatus status,
    int targetCount,
    Instant createdAt
) {

    public static CreateStampbookResponse from(CreateStampbookResult result) {
        return new CreateStampbookResponse(
            result.stampbookId().toString(),
            result.status(),
            result.targetCount(),
            result.createdAt()
        );
    }
}
