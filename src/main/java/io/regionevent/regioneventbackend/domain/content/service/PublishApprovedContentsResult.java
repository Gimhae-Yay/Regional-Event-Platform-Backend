package io.regionevent.regioneventbackend.domain.content.service;

import java.util.UUID;

public record PublishApprovedContentsResult(
    UUID requestId,
    int publishedContentCount,
    int skippedContentCount,
    int failedContentCount
) {
}
