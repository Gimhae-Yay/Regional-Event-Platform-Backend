package io.regionevent.regioneventbackend.domain.stampbook.dto;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookStatus;
import io.regionevent.regioneventbackend.domain.stampbook.service.UpdateStampbookResult;

public record UpdateStampbookResponse(
    String stampbookId,
    StampbookStatus status,
    int targetCount,
    Instant updatedAt
) {

    public static UpdateStampbookResponse from(UpdateStampbookResult result) {
        return new UpdateStampbookResponse(
            result.stampbookId().toString(),
            result.status(),
            result.targetCount(),
            result.updatedAt()
        );
    }
}
