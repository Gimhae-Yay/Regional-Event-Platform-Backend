package io.regionevent.regioneventbackend.domain.review.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.content.service.ContentService;

@Service
public class GetPublicContentReviewsUseCase {

    private static final String LINKED_AUTHOR_DISPLAY_NAME = "인증 방문자";
    private static final String UNLINKED_AUTHOR_DISPLAY_NAME = "탈퇴한 사용자";

    private final ContentService contentService;
    private final ReviewService reviewService;

    public GetPublicContentReviewsUseCase(
        ContentService contentService,
        ReviewService reviewService
    ) {
        this.contentService = contentService;
        this.reviewService = reviewService;
    }

    @Transactional(readOnly = true)
    public PublicContentReviewListResult get(Long contentId, int page, int size) {
        contentService.findPublicContent(contentId);
        Page<io.regionevent.regioneventbackend.domain.review.entity.Review> reviews =
            reviewService.findPublishedByContentId(contentId, PageRequest.of(page, size));
        List<PublicContentReviewListResult.Review> content = reviews.getContent().stream()
            .map(review -> new PublicContentReviewListResult.Review(
                review.getReviewId(),
                review.getUser() == null ? UNLINKED_AUTHOR_DISPLAY_NAME : LINKED_AUTHOR_DISPLAY_NAME,
                review.getRating(),
                review.getReviewText(),
                review.getCreatedAt(),
                review.getUpdatedAt()
            ))
            .toList();
        return new PublicContentReviewListResult(
            content,
            reviews.getNumber(),
            reviews.getSize(),
            reviews.getTotalElements(),
            reviews.getTotalPages()
        );
    }
}
