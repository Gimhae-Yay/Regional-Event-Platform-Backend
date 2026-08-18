package io.regionevent.regioneventbackend.domain.stampbook.service;

import java.time.Instant;
import java.util.List;

import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookStatus;

public record MyStampbookDetailResult(
    Long stampbookId,
    String title,
    Long regionId,
    StampbookStatus status,
    Instant publishedAt,
    Instant endedAt,
    List<TargetContent> targetContents,
    Progress progress
) {

    public record TargetContent(
        Long contentId,
        String title,
        boolean earned,
        Instant earnedAt
    ) {
    }

    public record Progress(
        MyStampbookProgressStatus status,
        long earnedCount,
        long targetCount,
        Instant completedAt,
        StampbookCompletionReward completionReward
    ) {
    }
}
