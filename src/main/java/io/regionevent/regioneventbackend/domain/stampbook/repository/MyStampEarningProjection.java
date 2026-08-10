package io.regionevent.regioneventbackend.domain.stampbook.repository;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookStatus;

public record MyStampEarningProjection(
    Long stampbookId,
    StampbookStatus stampbookStatus,
    Long stampbookProgressId,
    Long progressUserId,
    Long stampEarnId,
    Instant earnedAt,
    Long visitId,
    Long visitUserId,
    Long visitContentId,
    Instant visitedAt,
    Long contentId,
    String contentTitle
) {
}
