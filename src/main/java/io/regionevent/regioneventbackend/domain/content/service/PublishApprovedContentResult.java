package io.regionevent.regioneventbackend.domain.content.service;

import java.time.Duration;
import java.util.Objects;

public record PublishApprovedContentResult(
    Status status,
    Duration publicationDelay
) {

    public PublishApprovedContentResult {
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(publicationDelay, "publicationDelay must not be null");
        if (publicationDelay.isNegative()) {
            throw new IllegalArgumentException("publicationDelay must not be negative");
        }
        if (status == Status.SKIPPED && !publicationDelay.isZero()) {
            throw new IllegalArgumentException("skipped publicationDelay must be zero");
        }
    }

    public static PublishApprovedContentResult published(Duration publicationDelay) {
        return new PublishApprovedContentResult(Status.PUBLISHED, publicationDelay);
    }

    public static PublishApprovedContentResult skipped() {
        return new PublishApprovedContentResult(Status.SKIPPED, Duration.ZERO);
    }

    public enum Status {

        PUBLISHED,
        SKIPPED
    }
}
