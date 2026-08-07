package io.regionevent.regioneventbackend.domain.content.service;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLog;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLogStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;

@Component
public class OriginalContentReviewTargetPolicy {

    public OriginalContentReviewTarget classify(List<ContentLog> latestLogs) {
        if (latestLogs == null || latestLogs.isEmpty() || latestLogs.size() > 2) {
            throw new IllegalArgumentException("latestLogs must contain one or two logs");
        }

        ContentLog pendingLog = latestLogs.getFirst();
        Content content = pendingLog.getContent();
        validatePendingContent(content, pendingLog);

        if (latestLogs.size() == 1) {
            return new OriginalContentReviewTarget(
                content,
                pendingLog,
                null,
                OriginalContentReviewTargetType.INITIAL_SUBMISSION
            );
        }

        ContentLog previousLog = latestLogs.get(1);
        validateSameContent(content, previousLog);

        OriginalContentReviewTargetType type = switch (previousLog.getStatus()) {
            case REJECTED -> OriginalContentReviewTargetType.RESUBMISSION_AFTER_REJECTION;
            case APPROVED -> OriginalContentReviewTargetType.PRE_PUBLICATION_REVISION;
            default -> throw new IllegalStateException(
                "previous content log must be REJECTED or APPROVED but was " + previousLog.getStatus()
            );
        };

        return new OriginalContentReviewTarget(content, pendingLog, previousLog, type);
    }

    private void validatePendingContent(Content content, ContentLog pendingLog) {
        if (content.getDeletedAt() != null) {
            throw new IllegalStateException("content must not be soft deleted");
        }
        if (content.getStatus() != ContentStatus.PENDING) {
            throw new IllegalStateException("content status must be PENDING");
        }
        if (pendingLog.getStatus() != ContentLogStatus.PENDING) {
            throw new IllegalStateException("latest content log status must be PENDING");
        }
    }

    private void validateSameContent(Content content, ContentLog previousLog) {
        if (!Objects.equals(content.getContentId(), previousLog.getContent().getContentId())) {
            throw new IllegalStateException("content logs must belong to the same content");
        }
    }
}
