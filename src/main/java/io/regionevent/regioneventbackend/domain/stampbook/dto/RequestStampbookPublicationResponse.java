package io.regionevent.regioneventbackend.domain.stampbook.dto;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookStatus;
import io.regionevent.regioneventbackend.domain.stampbook.service.RequestStampbookPublicationResult;

public record RequestStampbookPublicationResponse(
    String stampbookId,
    StampbookStatus status,
    Instant requestedAt
) {

    public static RequestStampbookPublicationResponse from(
        RequestStampbookPublicationResult result
    ) {
        return new RequestStampbookPublicationResponse(
            result.stampbookId().toString(),
            result.status(),
            result.requestedAt()
        );
    }
}
