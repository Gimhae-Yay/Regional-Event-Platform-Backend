package io.regionevent.regioneventbackend.domain.content.service;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLog;

public record OriginalContentReviewTarget(
    Content content,
    ContentLog pendingLog,
    ContentLog previousLog,
    OriginalContentReviewTargetType type
) {

    public boolean isOriginalReviewTarget() {
        return type != OriginalContentReviewTargetType.PRE_PUBLICATION_REVISION;
    }
}
