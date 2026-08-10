package io.regionevent.regioneventbackend.domain.stampbook.service;

import java.time.Instant;
import java.util.List;

public record MyStampEarningsResult(
    Long stampbookId,
    List<Earning> earnings
) {

    public record Earning(
        Long stampEarnId,
        Long visitId,
        Long contentId,
        String contentTitle,
        Instant visitedAt,
        Instant earnedAt
    ) {
    }
}
