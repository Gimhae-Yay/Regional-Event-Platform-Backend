package io.regionevent.regioneventbackend.domain.stampbook.repository;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookProgressStatus;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookStatus;

public record MyStampbookListProjection(
    Long stampbookId,
    Long regionId,
    StampbookStatus stampbookStatus,
    Instant publishedAt,
    StampbookProgressStatus progressStatus,
    Instant completedAt,
    Long earnedCount,
    Long targetCount,
    Instant lastEarnedAt
) {
}
