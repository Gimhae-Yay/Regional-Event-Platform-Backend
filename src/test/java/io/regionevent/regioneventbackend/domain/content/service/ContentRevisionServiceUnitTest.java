package io.regionevent.regioneventbackend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevision;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevisionStatus;
import io.regionevent.regioneventbackend.domain.content.repository.ContentRevisionRepository;
import io.regionevent.regioneventbackend.domain.image.entity.ImageObject;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class ContentRevisionServiceUnitTest {

    private static final Long REGION_ID = 10L;

    @Test
    void 식별자로_심사_대기_수정본_상세_후보를_조회한다() {
        ContentRevisionRepository repository = mock(ContentRevisionRepository.class);
        ContentRevisionService service = new ContentRevisionService(repository);
        Long contentRevisionId = 501L;
        ContentRevision revision = mock(ContentRevision.class);
        Content content = mock(Content.class);
        AppUser operator = mock(AppUser.class);
        ImageObject candidateImageObject = mock(ImageObject.class);
        when(revision.getContent()).thenReturn(content);
        when(revision.getCandidateImageObject()).thenReturn(candidateImageObject);
        when(content.getOperator()).thenReturn(operator);
        when(repository.findByContentRevisionIdAndStatusAndContentDeletedAtIsNull(
            contentRevisionId,
            ContentRevisionStatus.EDIT_REQUESTED
        )).thenReturn(Optional.of(revision));

        ContentRevisionReviewCandidate candidate = service
            .findReviewCandidateById(contentRevisionId);

        assertThat(candidate).isEqualTo(new ContentRevisionReviewCandidate(
            revision,
            content,
            operator,
            candidateImageObject
        ));
    }

    @Test
    void 심사_대기_수정본이_아니면_존재를_노출하지_않는다() {
        ContentRevisionRepository repository = mock(ContentRevisionRepository.class);
        ContentRevisionService service = new ContentRevisionService(repository);
        Long contentRevisionId = 501L;
        when(repository.findByContentRevisionIdAndStatusAndContentDeletedAtIsNull(
            contentRevisionId,
            ContentRevisionStatus.EDIT_REQUESTED
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findReviewCandidateById(contentRevisionId))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND)
            );
    }

    @Test
    void 담당_지역의_심사_후보에_원본과_운영자와_후보_대표_이미지를_제공한다() {
        ContentRevisionRepository repository = mock(ContentRevisionRepository.class);
        ContentRevisionService service = new ContentRevisionService(repository);
        ContentRevision revision = mock(ContentRevision.class);
        Content content = mock(Content.class);
        AppUser operator = mock(AppUser.class);
        ImageObject candidateImageObject = mock(ImageObject.class);
        when(revision.getContent()).thenReturn(content);
        when(revision.getCandidateImageObject()).thenReturn(candidateImageObject);
        when(content.getOperator()).thenReturn(operator);
        when(repository
            .findByContentRegionRegionIdAndStatusAndContentDeletedAtIsNullOrderBySubmittedAtAscContentRevisionIdAsc(
                REGION_ID,
                ContentRevisionStatus.EDIT_REQUESTED
            ))
            .thenReturn(List.of(revision));

        List<ContentRevisionReviewCandidate> candidates = service
            .findReviewCandidatesByRegionId(REGION_ID);

        assertThat(candidates).containsExactly(new ContentRevisionReviewCandidate(
            revision,
            content,
            operator,
            candidateImageObject
        ));
        verify(repository)
            .findByContentRegionRegionIdAndStatusAndContentDeletedAtIsNullOrderBySubmittedAtAscContentRevisionIdAsc(
                REGION_ID,
                ContentRevisionStatus.EDIT_REQUESTED
            );
    }

    @Test
    void 후보_대표_이미지_연결이_없으면_정합성_오류로_처리한다() {
        ContentRevisionRepository repository = mock(ContentRevisionRepository.class);
        ContentRevisionService service = new ContentRevisionService(repository);
        ContentRevision revision = mock(ContentRevision.class);
        when(revision.getContent()).thenReturn(mock(Content.class));
        when(repository
            .findByContentRegionRegionIdAndStatusAndContentDeletedAtIsNullOrderBySubmittedAtAscContentRevisionIdAsc(
                REGION_ID,
                ContentRevisionStatus.EDIT_REQUESTED
            ))
            .thenReturn(List.of(revision));

        assertThatThrownBy(() -> service.findReviewCandidatesByRegionId(REGION_ID))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("review candidate must have a candidate image object");
    }
}
