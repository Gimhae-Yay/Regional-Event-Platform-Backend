package io.regionevent.regioneventbackend.domain.content.dto;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import io.regionevent.regioneventbackend.domain.content.service.ContentWithdrawalReviewDetailResult;

public record ContentWithdrawalReviewDetailResponse(
    String withdrawalRequestId,
    String status,
    Content content,
    Requester requester,
    String requestReason,
    Instant requestedAt
) {

    private static final ZoneId SEOUL_TIME_ZONE = ZoneId.of("Asia/Seoul");

    public static ContentWithdrawalReviewDetailResponse from(
        ContentWithdrawalReviewDetailResult result
    ) {
        return new ContentWithdrawalReviewDetailResponse(
            result.withdrawalRequestId().toString(),
            result.status().name(),
            Content.from(result.content()),
            result.requester() == null ? null : Requester.from(result.requester()),
            result.requestReason(),
            result.requestedAt()
        );
    }

    public record Content(
        String contentId,
        String contentType,
        String title,
        String status,
        OffsetDateTime publishAt
    ) {

        private static Content from(ContentWithdrawalReviewDetailResult.Content content) {
            return new Content(
                content.contentId().toString(),
                content.contentType().name(),
                content.title(),
                content.status().name(),
                content.publishAt().atZone(SEOUL_TIME_ZONE).toOffsetDateTime()
            );
        }
    }

    public record Requester(
        String userId,
        String name
    ) {

        private static Requester from(ContentWithdrawalReviewDetailResult.Requester requester) {
            return new Requester(requester.userId().toString(), requester.name());
        }
    }
}
