package io.regionevent.regioneventbackend.domain.content.service;

import java.time.Instant;
import java.util.List;

import io.regionevent.regioneventbackend.domain.content.entity.ContentLogStatus;

public record ContentHistoryResult(
    Long contentId,
    List<History> histories
) {

    public ContentHistoryResult {
        histories = List.copyOf(histories);
    }

    public record History(
        ContentLogStatus status,
        String reason,
        Instant processedAt,
        Actor actor
    ) {
    }

    public record Actor(
        Long userId,
        String displayName
    ) {
    }
}
