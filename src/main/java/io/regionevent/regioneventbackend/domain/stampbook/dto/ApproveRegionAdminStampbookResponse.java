package io.regionevent.regioneventbackend.domain.stampbook.dto;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookStatus;
import io.regionevent.regioneventbackend.domain.stampbook.service.ApproveRegionAdminStampbookResult;

public record ApproveRegionAdminStampbookResponse(
    String stampbookId,
    StampbookStatus status,
    Instant publishedAt
) {

    public static ApproveRegionAdminStampbookResponse from(
        ApproveRegionAdminStampbookResult result
    ) {
        return new ApproveRegionAdminStampbookResponse(
            result.stampbookId().toString(),
            result.status(),
            result.publishedAt()
        );
    }
}
