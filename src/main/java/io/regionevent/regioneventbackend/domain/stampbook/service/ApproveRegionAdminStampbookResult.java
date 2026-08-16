package io.regionevent.regioneventbackend.domain.stampbook.service;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.stampbook.entity.Stampbook;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookStatus;

public record ApproveRegionAdminStampbookResult(
    Long stampbookId,
    StampbookStatus status,
    Instant publishedAt
) {

    public static ApproveRegionAdminStampbookResult from(Stampbook stampbook) {
        return new ApproveRegionAdminStampbookResult(
            stampbook.getStampbookId(),
            stampbook.getStatus(),
            stampbook.getPublishedAt()
        );
    }
}
