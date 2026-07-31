package io.regionevent.regioneventbackend.domain.content.dto;

import java.time.Instant;
import java.util.List;

import io.regionevent.regioneventbackend.domain.content.service.ContentHistoryResult;

public record ContentHistoryResponse(
    Long contentId,
    List<History> histories
) {

    public ContentHistoryResponse {
        histories = List.copyOf(histories);
    }

    public static ContentHistoryResponse from(ContentHistoryResult result) {
        return new ContentHistoryResponse(
            result.contentId(),
            result.histories().stream()
                .map(History::from)
                .toList()
        );
    }

    public record History(
        String status,
        String reason,
        Instant processedAt,
        Actor actor
    ) {

        private static History from(ContentHistoryResult.History history) {
            return new History(
                history.status().name(),
                history.reason(),
                history.processedAt(),
                Actor.from(history.actor())
            );
        }
    }

    public record Actor(
        Long userId,
        String displayName
    ) {

        private static Actor from(ContentHistoryResult.Actor actor) {
            if (actor == null) {
                return null;
            }
            return new Actor(actor.userId(), actor.displayName());
        }
    }
}
