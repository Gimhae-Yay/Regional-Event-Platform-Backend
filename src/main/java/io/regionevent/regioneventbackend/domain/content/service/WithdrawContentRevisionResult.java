package io.regionevent.regioneventbackend.domain.content.service;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.content.entity.ContentRevision;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevisionStatus;

public record WithdrawContentRevisionResult(
    Long revisionId,
    Long contentId,
    ContentRevisionStatus status,
    String withdrawalReason,
    Instant withdrawnAt
) {

    public static WithdrawContentRevisionResult from(ContentRevision revision) {
        return new WithdrawContentRevisionResult(
            revision.getContentRevisionId(),
            revision.getContent().getContentId(),
            revision.getStatus(),
            revision.getWithdrawalReason(),
            revision.getWithdrawnAt()
        );
    }
}
