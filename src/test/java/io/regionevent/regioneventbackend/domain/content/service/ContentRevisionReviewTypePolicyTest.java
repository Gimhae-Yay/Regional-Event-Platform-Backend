package io.regionevent.regioneventbackend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevision;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.image.entity.ImageObject;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;

class ContentRevisionReviewTypePolicyTest {

    private static final Instant CANDIDATE_PUBLISH_AT = Instant.parse("2026-08-20T00:00:00Z");

    private final ContentRevisionReviewTypePolicy policy = new ContentRevisionReviewTypePolicy();

    @Test
    void 후보_공개_시각이_없고_원본이_공개_상태이면_공개_콘텐츠_수정본으로_분류한다() {
        ContentRevisionReviewCandidate candidate = candidate(ContentStatus.PUBLISHED, false);

        ContentRevisionReviewType reviewType = policy.classify(candidate, false);

        assertThat(reviewType).isEqualTo(ContentRevisionReviewType.PUBLISHED_REVISION);
    }

    @Test
    void 후보_공개_시각과_승인_후_PENDING_이력이_있으면_공개_전_수정본으로_분류한다() {
        ContentRevisionReviewCandidate candidate = candidate(ContentStatus.PENDING, true);

        ContentRevisionReviewType reviewType = policy.classify(candidate, true);

        assertThat(reviewType).isEqualTo(ContentRevisionReviewType.PRE_PUBLIC_REVISION);
    }

    @ParameterizedTest
    @ArgumentsSource(InvalidReviewStateProvider.class)
    void 정상_조합이_아닌_모든_수정본_심사_상태는_정합성_오류로_처리한다(
        ContentStatus contentStatus,
        boolean hasCandidatePublishAt,
        boolean isPrePublicationRevisionByHistory
    ) {
        ContentRevisionReviewCandidate candidate = candidate(contentStatus, hasCandidatePublishAt);

        assertThatThrownBy(() -> policy.classify(candidate, isPrePublicationRevisionByHistory))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("content revision review state is inconsistent")
            .hasMessageContaining("contentStatus=" + contentStatus)
            .hasMessageContaining("hasCandidatePublishAt=" + hasCandidatePublishAt)
            .hasMessageContaining(
                "isPrePublicationRevisionByHistory=" + isPrePublicationRevisionByHistory
            );
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

    static class InvalidReviewStateProvider implements ArgumentsProvider {

        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
            Stream.Builder<Arguments> arguments = Stream.builder();
            for (ContentStatus contentStatus : ContentStatus.values()) {
                for (boolean hasCandidatePublishAt : new boolean[]{false, true}) {
                    for (boolean isPrePublicationRevisionByHistory : new boolean[]{false, true}) {
                        if (!isValid(
                            contentStatus,
                            hasCandidatePublishAt,
                            isPrePublicationRevisionByHistory
                        )) {
                            arguments.add(Arguments.of(
                                contentStatus,
                                hasCandidatePublishAt,
                                isPrePublicationRevisionByHistory
                            ));
                        }
                    }
                }
            }
            return arguments.build();
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
}
