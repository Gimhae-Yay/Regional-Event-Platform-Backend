package io.regionevent.regioneventbackend.domain.stampbook.dto;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookStatus;
import io.regionevent.regioneventbackend.domain.stampbook.service.RejectRegionAdminStampbookResult;

public record RejectRegionAdminStampbookResponse(
    String stampbookId,
    StampbookStatus status,
    Instant rejectedAt
) {

    public static RejectRegionAdminStampbookResponse from(
        RejectRegionAdminStampbookResult result
    ) {
        return new RejectRegionAdminStampbookResponse(
            result.stampbookId().toString(),
            result.status(),
            result.rejectedAt()
        );
    }
}
