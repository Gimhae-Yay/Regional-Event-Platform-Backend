package io.regionevent.regioneventbackend.domain.stampbook.service;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.stampbook.entity.Stampbook;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookStatus;

public record RejectRegionAdminStampbookResult(
    Long stampbookId,
    StampbookStatus status,
    Instant rejectedAt
) {

    public static RejectRegionAdminStampbookResult from(
        Stampbook stampbook,
        Instant rejectedAt
    ) {
        return new RejectRegionAdminStampbookResult(
            stampbook.getStampbookId(),
            stampbook.getStatus(),
            rejectedAt
        );
    }
}
