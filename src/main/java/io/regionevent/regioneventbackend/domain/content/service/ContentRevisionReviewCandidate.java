package io.regionevent.regioneventbackend.domain.content.service;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevision;
import io.regionevent.regioneventbackend.domain.image.entity.ImageObject;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;

public record ContentRevisionReviewCandidate(
    ContentRevision revision,
    Content content,
    AppUser operator,
    ImageObject candidateImageObject
) {

    public static ContentRevisionReviewCandidate from(ContentRevision revision) {
        Content content = revision.getContent();
        ImageObject candidateImageObject = revision.getCandidateImageObject();
        if (candidateImageObject == null) {
            throw new IllegalStateException("review candidate must have a candidate image object");
        }
        return new ContentRevisionReviewCandidate(
            revision,
            content,
            content.getOperator(),
            candidateImageObject
        );
    }
}
