package io.regionevent.regioneventbackend.domain.content.service;

import org.springframework.stereotype.Component;

import io.regionevent.regioneventbackend.domain.content.entity.ContentRevision;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;

@Component
public class ContentRevisionReviewTypePolicy {

    public ContentRevisionReviewType classify(
        ContentRevisionReviewCandidate candidate,
        boolean isPrePublicationRevisionByHistory
    ) {
        ContentRevision revision = candidate.revision();
        ContentStatus contentStatus = candidate.content().getStatus();
        boolean hasCandidatePublishAt = revision.getPublishAt() != null;

        if (!hasCandidatePublishAt
            && contentStatus == ContentStatus.PUBLISHED
            && !isPrePublicationRevisionByHistory) {
            return ContentRevisionReviewType.PUBLISHED_REVISION;
        }
        if (hasCandidatePublishAt
            && contentStatus == ContentStatus.PENDING
            && isPrePublicationRevisionByHistory) {
            return ContentRevisionReviewType.PRE_PUBLIC_REVISION;
        }
        throw new IllegalStateException(
            "content revision review state is inconsistent: contentStatus=" + contentStatus
                + ", hasCandidatePublishAt=" + hasCandidatePublishAt
                + ", isPrePublicationRevisionByHistory=" + isPrePublicationRevisionByHistory
        );
    }
}
