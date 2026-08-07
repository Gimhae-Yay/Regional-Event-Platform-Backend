package io.regionevent.regioneventbackend.domain.content.service;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record PublishApprovedContentsResult(
    UUID requestId,
    int candidateContentCount,
    int publishedContentCount,
    int skippedContentCount,
    int failedContentCount,
    List<Duration> publicationDelays
) {

    public PublishApprovedContentsResult {
        if (candidateContentCount < 0
            || publishedContentCount < 0
            || skippedContentCount < 0
            || failedContentCount < 0) {
            throw new IllegalArgumentException("publication counts must not be negative");
        }
        if (candidateContentCount != publishedContentCount + skippedContentCount + failedContentCount) {
            throw new IllegalArgumentException("candidateContentCount must match publication result counts");
        }
        publicationDelays = List.copyOf(publicationDelays);
        if (publishedContentCount != publicationDelays.size()) {
            throw new IllegalArgumentException("publishedContentCount must match publicationDelays size");
        }
    }

    public Duration maximumPublicationDelay() {
        return publicationDelays.stream()
            .max(Comparator.naturalOrder())
            .orElse(Duration.ZERO);
    }
}
