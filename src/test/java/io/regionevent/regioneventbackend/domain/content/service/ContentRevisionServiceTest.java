package io.regionevent.regioneventbackend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevision;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevisionStatus;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRevisionRepository;
import io.regionevent.regioneventbackend.domain.image.entity.ImageObject;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;

class ContentRevisionServiceTest {

    private static final Long REGION_ID = 10L;

    private final ContentRevisionRepository contentRevisionRepository = mock(ContentRevisionRepository.class);
    private final ContentRevisionService contentRevisionService = new ContentRevisionService(contentRevisionRepository);

    @Test
    void 담당_지역의_심사_후보에_원본과_운영자와_후보_대표_이미지를_제공한다() {
        ContentRevision revision = mock(ContentRevision.class);
        Content content = mock(Content.class);
        AppUser operator = mock(AppUser.class);
        ImageObject candidateImageObject = mock(ImageObject.class);
        when(revision.getContent()).thenReturn(content);
        when(revision.getCandidateImageObject()).thenReturn(candidateImageObject);
        when(content.getOperator()).thenReturn(operator);
        when(contentRevisionRepository
            .findByContentRegionRegionIdAndStatusAndContentDeletedAtIsNullOrderBySubmittedAtAscContentRevisionIdAsc(
                REGION_ID,
                ContentRevisionStatus.EDIT_REQUESTED
            ))
            .thenReturn(List.of(revision));

        List<ContentRevisionReviewCandidate> candidates = contentRevisionService
            .findReviewCandidatesByRegionId(REGION_ID);

        assertThat(candidates).containsExactly(new ContentRevisionReviewCandidate(
            revision,
            content,
            operator,
            candidateImageObject
        ));
        verify(contentRevisionRepository)
            .findByContentRegionRegionIdAndStatusAndContentDeletedAtIsNullOrderBySubmittedAtAscContentRevisionIdAsc(
                REGION_ID,
                ContentRevisionStatus.EDIT_REQUESTED
            );
    }

    @Test
    void 후보_대표_이미지_연결이_없으면_정합성_오류로_처리한다() {
        ContentRevision revision = mock(ContentRevision.class);
        when(revision.getContent()).thenReturn(mock(Content.class));
        when(contentRevisionRepository
            .findByContentRegionRegionIdAndStatusAndContentDeletedAtIsNullOrderBySubmittedAtAscContentRevisionIdAsc(
                REGION_ID,
                ContentRevisionStatus.EDIT_REQUESTED
            ))
            .thenReturn(List.of(revision));

        assertThatThrownBy(() -> contentRevisionService.findReviewCandidatesByRegionId(REGION_ID))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("review candidate must have a candidate image object");
    }
}
