package io.regionevent.regioneventbackend.domain.stampbook.dto;

import java.time.Instant;
import java.util.List;

import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookStatus;
import io.regionevent.regioneventbackend.domain.stampbook.service.MyStampbookDetailResult;
import io.regionevent.regioneventbackend.domain.stampbook.service.MyStampbookProgressStatus;

public record GetMyStampbookDetailResponse(
    StampbookResponse stampbook,
    ProgressResponse progress
) {

    public static GetMyStampbookDetailResponse from(MyStampbookDetailResult result) {
        return new GetMyStampbookDetailResponse(
            new StampbookResponse(
                result.stampbookId().toString(),
                result.regionId().toString(),
                result.status(),
                result.publishedAt(),
                result.endedAt(),
                result.targetContents().stream()
                    .map(TargetContentResponse::from)
                    .toList()
            ),
            new ProgressResponse(
                result.progress().status(),
                result.progress().earnedCount(),
                result.progress().targetCount(),
                result.progress().completedAt()
            )
        );
    }

    public record StampbookResponse(
        String stampbookId,
        String regionId,
        StampbookStatus status,
        Instant publishedAt,
        Instant endedAt,
        List<TargetContentResponse> targetContents
    ) {
    }

    public record TargetContentResponse(
        String contentId,
        String title,
        boolean earned,
        Instant earnedAt
    ) {

        private static TargetContentResponse from(MyStampbookDetailResult.TargetContent targetContent) {
            return new TargetContentResponse(
                targetContent.contentId().toString(),
                targetContent.title(),
                targetContent.earned(),
                targetContent.earnedAt()
            );
        }
    }

    public record ProgressResponse(
        MyStampbookProgressStatus status,
        long earnedCount,
        long targetCount,
        Instant completedAt
    ) {
    }
}
