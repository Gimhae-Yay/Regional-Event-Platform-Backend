package io.regionevent.regioneventbackend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevision;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.image.entity.ImageObject;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;

class ContentRevisionReviewTypePolicyTest {

    private static final Instant CANDIDATE_PUBLISH_AT = Instant.parse("2026-08-20T00:00:00Z");

    private final ContentRevisionReviewTypePolicy policy = new ContentRevisionReviewTypePolicy();

    @Test
    void 수정본_심사_유형_분류_계약을_보존한다() {
        assertAll(
            () -> new ContentRevisionReviewTypePolicyTest()
                .후보_공개_시각이_없고_원본이_공개_상태이면_공개_콘텐츠_수정본으로_분류한다(),
            () -> new ContentRevisionReviewTypePolicyTest()
                .후보_공개_시각과_승인_후_PENDING_이력이_있으면_공개_전_수정본으로_분류한다(),
            () -> new ContentRevisionReviewTypePolicyTest()
                .정상_조합이_아닌_모든_수정본_심사_상태는_정합성_오류로_처리한다()
        );
    }

    void 후보_공개_시각이_없고_원본이_공개_상태이면_공개_콘텐츠_수정본으로_분류한다() {
        ContentRevisionReviewCandidate candidate = candidate(ContentStatus.PUBLISHED, false);

        ContentRevisionReviewType reviewType = policy.classify(candidate, false);

        assertThat(reviewType).isEqualTo(ContentRevisionReviewType.PUBLISHED_REVISION);
    }

    void 후보_공개_시각과_승인_후_PENDING_이력이_있으면_공개_전_수정본으로_분류한다() {
        ContentRevisionReviewCandidate candidate = candidate(ContentStatus.PENDING, true);

        ContentRevisionReviewType reviewType = policy.classify(candidate, true);

        assertThat(reviewType).isEqualTo(ContentRevisionReviewType.PRE_PUBLIC_REVISION);
    }

    void 정상_조합이_아닌_모든_수정본_심사_상태는_정합성_오류로_처리한다() {
        List<Executable> contracts = new ArrayList<>();
        for (ContentStatus contentStatus : ContentStatus.values()) {
            for (boolean hasCandidatePublishAt : new boolean[]{false, true}) {
                for (boolean isPrePublicationRevisionByHistory : new boolean[]{false, true}) {
                    if (!isValid(
                        contentStatus,
                        hasCandidatePublishAt,
                        isPrePublicationRevisionByHistory
                    )) {
                        contracts.add(() -> {
                            ContentRevisionReviewCandidate candidate = candidate(
                                contentStatus,
                                hasCandidatePublishAt
                            );

                            assertThatThrownBy(() -> policy.classify(
                                candidate,
                                isPrePublicationRevisionByHistory
                            )).isInstanceOf(IllegalStateException.class)
                                .hasMessageContaining("content revision review state is inconsistent")
                                .hasMessageContaining("contentStatus=" + contentStatus)
                                .hasMessageContaining("hasCandidatePublishAt=" + hasCandidatePublishAt)
                                .hasMessageContaining(
                                    "isPrePublicationRevisionByHistory="
                                        + isPrePublicationRevisionByHistory
                                );
                        });
                    }
                }
            }
        }

        assertAll(contracts);
    }

    private ContentRevisionReviewCandidate candidate(
        ContentStatus contentStatus,
        boolean hasCandidatePublishAt
    ) {
        ContentRevision revision = mock(ContentRevision.class);
        Content content = mock(Content.class);
        when(revision.getPublishAt()).thenReturn(hasCandidatePublishAt ? CANDIDATE_PUBLISH_AT : null);
        when(content.getStatus()).thenReturn(contentStatus);
        return new ContentRevisionReviewCandidate(
            revision,
            content,
            mock(AppUser.class),
            mock(ImageObject.class)
        );
    }

    private static boolean isValid(
        ContentStatus contentStatus,
        boolean hasCandidatePublishAt,
        boolean isPrePublicationRevisionByHistory
    ) {
        return contentStatus == ContentStatus.PUBLISHED
            && !hasCandidatePublishAt
            && !isPrePublicationRevisionByHistory
            || contentStatus == ContentStatus.PENDING
            && hasCandidatePublishAt
            && isPrePublicationRevisionByHistory;
    }
}
