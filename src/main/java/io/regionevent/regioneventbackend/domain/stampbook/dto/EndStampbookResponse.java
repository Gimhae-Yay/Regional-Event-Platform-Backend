package io.regionevent.regioneventbackend.domain.stampbook.dto;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookStatus;
import io.regionevent.regioneventbackend.domain.stampbook.service.EndStampbookResult;

public record EndStampbookResponse(
    String stampbookId,
    StampbookStatus status,
    Instant endedAt
) {

    public static EndStampbookResponse from(EndStampbookResult result) {
        return new EndStampbookResponse(
            result.stampbookId().toString(),
            result.status(),
            result.endedAt()
        );
    }
}
