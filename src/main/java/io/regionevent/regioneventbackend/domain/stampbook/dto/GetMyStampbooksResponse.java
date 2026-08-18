package io.regionevent.regioneventbackend.domain.stampbook.dto;

import java.time.Instant;
import java.util.List;

import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookStatus;
import io.regionevent.regioneventbackend.domain.stampbook.service.MyStampbookListResult;
import io.regionevent.regioneventbackend.domain.stampbook.service.MyStampbookProgressStatus;
import io.regionevent.regioneventbackend.domain.stampbook.service.StampbookCompletionReward;

public record GetMyStampbooksResponse(List<StampbookResponse> stampbooks) {

    public static GetMyStampbooksResponse from(List<MyStampbookListResult> results) {
        return new GetMyStampbooksResponse(results.stream()
            .map(StampbookResponse::from)
            .toList());
    }

    public record StampbookResponse(
        String stampbookId,
        String title,
        String regionId,
        StampbookStatus status,
        Instant publishedAt,
        ProgressResponse progress
    ) {

        private static StampbookResponse from(MyStampbookListResult result) {
            return new StampbookResponse(
                result.stampbookId().toString(),
                result.title(),
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
        Instant lastEarnedAt,
        CompletionRewardResponse completionReward
    ) {

        private static ProgressResponse from(MyStampbookListResult.Progress progress) {
            return new ProgressResponse(
                progress.status(),
                progress.earnedCount(),
                progress.targetCount(),
                progress.completedAt(),
                progress.lastEarnedAt(),
                CompletionRewardResponse.from(progress.completionReward())
            );
        }
    }

    public record CompletionRewardResponse(
        String couponPolicyId,
        String stampbookRewardGrantId
    ) {

        private static CompletionRewardResponse from(StampbookCompletionReward reward) {
            if (reward == null) {
                return null;
            }
            return new CompletionRewardResponse(
                reward.couponPolicyId().toString(),
                reward.stampbookRewardGrantId().toString()
            );
        }
    }
}
