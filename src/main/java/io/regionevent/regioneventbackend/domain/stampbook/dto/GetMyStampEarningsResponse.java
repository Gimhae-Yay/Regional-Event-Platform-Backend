package io.regionevent.regioneventbackend.domain.stampbook.dto;

import java.time.Instant;
import java.util.List;

import io.regionevent.regioneventbackend.domain.stampbook.service.MyStampEarningsResult;

public record GetMyStampEarningsResponse(
    String stampbookId,
    List<EarningResponse> earnings
) {

    public static GetMyStampEarningsResponse from(MyStampEarningsResult result) {
        return new GetMyStampEarningsResponse(
            result.stampbookId().toString(),
            result.earnings().stream()
                .map(EarningResponse::from)
                .toList()
        );
    }

    public record EarningResponse(
        String stampEarnId,
        String visitId,
        ContentResponse content,
        Instant visitedAt,
        Instant earnedAt
    ) {

        private static EarningResponse from(MyStampEarningsResult.Earning earning) {
            return new EarningResponse(
                earning.stampEarnId().toString(),
                earning.visitId().toString(),
                new ContentResponse(earning.contentId().toString(), earning.contentTitle()),
                earning.visitedAt(),
                earning.earnedAt()
            );
        }
    }

    public record ContentResponse(
        String contentId,
        String title
    ) {
    }
}
