package io.regionevent.regioneventbackend.domain.stampbook.dto;

import java.time.Instant;
import java.util.List;

import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookStatus;
import io.regionevent.regioneventbackend.domain.stampbook.service.MyStampbookListResult;
import io.regionevent.regioneventbackend.domain.stampbook.service.MyStampbookProgressStatus;

public record GetMyStampbooksResponse(List<StampbookResponse> stampbooks) {

    public static GetMyStampbooksResponse from(List<MyStampbookListResult> results) {
        return new GetMyStampbooksResponse(results.stream()
            .map(StampbookResponse::from)
            .toList());
    }

    public record StampbookResponse(
        String stampbookId,
        String regionId,
        StampbookStatus status,
        Instant publishedAt,
        ProgressResponse progress
    ) {

        private static StampbookResponse from(MyStampbookListResult result) {
            return new StampbookResponse(
                result.stampbookId().toString(),
                result.regionId().toString(),
                result.status(),
                result.publishedAt(),
                ProgressResponse.from(result.progress())
            );
        }
    }

    public record ProgressResponse(
        MyStampbookProgressStatus status,
        long earnedCount,
        long targetCount,
        Instant completedAt,
        Instant lastEarnedAt
    ) {

        private static ProgressResponse from(MyStampbookListResult.Progress progress) {
            return new ProgressResponse(
                progress.status(),
                progress.earnedCount(),
                progress.targetCount(),
                progress.completedAt(),
                progress.lastEarnedAt()
            );
        }
    }
}
