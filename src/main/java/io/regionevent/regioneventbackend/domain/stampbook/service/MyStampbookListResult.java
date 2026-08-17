package io.regionevent.regioneventbackend.domain.stampbook.service;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookStatus;

public record MyStampbookListResult(
    Long stampbookId,
    Long regionId,
    StampbookStatus status,
    Instant publishedAt,
    Progress progress
) {

    public record Progress(
        MyStampbookProgressStatus status,
        long earnedCount,
        long targetCount,
        Instant completedAt,
        Instant lastEarnedAt,
        StampbookCompletionReward completionReward
    ) {
    }
}
