package io.regionevent.regioneventbackend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevision;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevisionStatus;
import io.regionevent.regioneventbackend.domain.image.entity.ImageObject;
import io.regionevent.regioneventbackend.domain.image.service.RepresentativeImageViewUrl;
import io.regionevent.regioneventbackend.domain.image.service.RepresentativeImageViewUrlService;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class GetLatestContentRevisionUseCaseTest {

    private static final Long USER_ID = 100L;
    private static final Long CONTENT_ID = 101L;
    private static final Long REVISION_ID = 501L;
    private static final Long REGION_ID = 10L;
    private static final Instant SUBMITTED_AT = Instant.parse("2026-08-18T01:00:00Z");
    private static final Instant REVIEWED_AT = Instant.parse("2026-08-18T03:00:00Z");
    private static final Instant IMAGE_EXPIRES_AT = Instant.parse("2026-08-18T04:00:00Z");

    private final ContentService contentService = mock(ContentService.class);
    private final ContentRevisionService contentRevisionService = mock(ContentRevisionService.class);
    private final OperatorAuthorizationService operatorAuthorizationService =
        mock(OperatorAuthorizationService.class);
    private final RepresentativeImageViewUrlService representativeImageViewUrlService =
        mock(RepresentativeImageViewUrlService.class);
    private final GetLatestContentRevisionUseCase useCase = new GetLatestContentRevisionUseCase(
        contentService,
        contentRevisionService,
        operatorAuthorizationService,
        representativeImageViewUrlService
    );

    @Test
    void get_소유_운영자에게_현재_원본과_혼합하지_않은_최신_반려_수정본을_반환한다() {
        RevisionFixture fixture = revisionFixture(ContentRevisionStatus.EDIT_REJECTED);
        stubSuccessfulLookup(fixture);

        LatestContentRevisionDetailResult result = useCase.get(USER_ID, CONTENT_ID);

        assertThat(result.revisionId()).isEqualTo(REVISION_ID);
        assertThat(result.contentId()).isEqualTo(CONTENT_ID);
        assertThat(result.revisionNo()).isEqualTo(2);
        assertThat(result.baseContentVersion()).isEqualTo(3);
        assertThat(result.status()).isEqualTo(ContentRevisionStatus.EDIT_REJECTED);
        assertThat(result.title()).isEqualTo("후보 제목");
        assertThat(result.reservationPrice()).isEqualTo(20_000);
        assertThat(result.reviewReason()).isEqualTo("대표 이미지를 보완해 주세요.");
        assertThat(result.reviewedAt()).isEqualTo(REVIEWED_AT);
        assertThat(result.representativeImageUrl()).isEqualTo("https://example.invalid/image");
        assertThat(result.representativeImageUrlExpiresAt()).isEqualTo(IMAGE_EXPIRES_AT);

        InOrder inOrder = inOrder(
            contentService,
            operatorAuthorizationService,
            contentRevisionService,
            representativeImageViewUrlService
        );
        inOrder.verify(contentService).findMyContentDetail(CONTENT_ID);
        inOrder.verify(operatorAuthorizationService).authorizeOwnedContent(
            USER_ID,
            fixture.operator(),
            fixture.region()
        );
        inOrder.verify(contentRevisionService).findLatestRevisionByContentId(CONTENT_ID);
        inOrder.verify(representativeImageViewUrlService).createViewUrl(fixture.candidateImage());
    }

    @ParameterizedTest
    @EnumSource(ContentRevisionStatus.class)
    void get_모든_수정본_상태의_유효한_심사_정보를_반환한다(ContentRevisionStatus status) {
        RevisionFixture fixture = revisionFixture(status);
        stubSuccessfulLookup(fixture);

        LatestContentRevisionDetailResult result = useCase.get(USER_ID, CONTENT_ID);

        assertThat(result.status()).isEqualTo(status);
        if (status == ContentRevisionStatus.EDIT_REJECTED) {
            assertThat(result.reviewReason()).isEqualTo("대표 이미지를 보완해 주세요.");
            assertThat(result.reviewedAt()).isEqualTo(REVIEWED_AT);
        } else if (status == ContentRevisionStatus.EDIT_APPROVED) {
            assertThat(result.reviewReason()).isNull();
            assertThat(result.reviewedAt()).isEqualTo(REVIEWED_AT);
        } else {
            assertThat(result.reviewReason()).isNull();
            assertThat(result.reviewedAt()).isNull();
        }
    }

    @Test
    void get_후보_이미지_연결이_없으면_URL을_발급하지_않고_서버_오류를_반환한다() {
        RevisionFixture fixture = revisionFixture(ContentRevisionStatus.EDIT_REQUESTED);
        when(fixture.revision().getCandidateImageAssignedAt()).thenReturn(null);
        when(contentService.findMyContentDetail(CONTENT_ID)).thenReturn(fixture.content());
        when(contentRevisionService.findLatestRevisionByContentId(CONTENT_ID)).thenReturn(fixture.revision());

        assertInternalServerError();
        verifyNoInteractions(representativeImageViewUrlService);
    }

    @Test
    void get_반려_수정본의_반려_사유가_비어_있으면_서버_오류를_반환한다() {
        RevisionFixture fixture = revisionFixture(ContentRevisionStatus.EDIT_REJECTED);
        when(fixture.revision().getReviewReason()).thenReturn(" ");
        stubLookupBeforeImage(fixture);

        assertInternalServerError();
        verifyNoInteractions(representativeImageViewUrlService);
    }

    @Test
    void get_승인_수정본의_심사_시각이_없으면_서버_오류를_반환한다() {
        RevisionFixture fixture = revisionFixture(ContentRevisionStatus.EDIT_APPROVED);
        when(fixture.revision().getReviewedAt()).thenReturn(null);
        stubLookupBeforeImage(fixture);

        assertInternalServerError();
        verifyNoInteractions(representativeImageViewUrlService);
    }

    @Test
    void get_심사되지_않은_수정본에_심사_정보가_있으면_서버_오류를_반환한다() {
        RevisionFixture fixture = revisionFixture(ContentRevisionStatus.EDIT_WITHDRAWN);
        when(fixture.revision().getReviewedAt()).thenReturn(REVIEWED_AT);
        stubLookupBeforeImage(fixture);

        assertInternalServerError();
        verifyNoInteractions(representativeImageViewUrlService);
    }

    private void stubSuccessfulLookup(RevisionFixture fixture) {
        stubLookupBeforeImage(fixture);
        when(representativeImageViewUrlService.createViewUrl(fixture.candidateImage()))
            .thenReturn(new RepresentativeImageViewUrl(
                "https://example.invalid/image",
                IMAGE_EXPIRES_AT
            ));
    }

    private void stubLookupBeforeImage(RevisionFixture fixture) {
        when(contentService.findMyContentDetail(CONTENT_ID)).thenReturn(fixture.content());
        when(contentRevisionService.findLatestRevisionByContentId(CONTENT_ID)).thenReturn(fixture.revision());
    }

    private void assertInternalServerError() {
        assertThatThrownBy(() -> useCase.get(USER_ID, CONTENT_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR)
            );
    }

    private RevisionFixture revisionFixture(ContentRevisionStatus status) {
        Content content = mock(Content.class);
        ContentRevision revision = mock(ContentRevision.class);
        AppUser operator = mock(AppUser.class);
        AppUser reviewer = mock(AppUser.class);
        Region region = mock(Region.class);
        ImageObject candidateImage = mock(ImageObject.class);

        when(content.getOperator()).thenReturn(operator);
        when(content.getRegion()).thenReturn(region);
        when(region.getRegionId()).thenReturn(REGION_ID);
        when(revision.getContentRevisionId()).thenReturn(REVISION_ID);
        when(revision.getRevisionNo()).thenReturn(2);
        when(revision.getBaseContentVersion()).thenReturn(3);
        when(revision.getStatus()).thenReturn(status);
        when(revision.getTitle()).thenReturn("후보 제목");
        when(revision.getDescription()).thenReturn("후보 설명");
        when(revision.getCandidateImageObject()).thenReturn(candidateImage);
        when(revision.getCandidateImageAssignedAt()).thenReturn(SUBMITTED_AT);
        when(revision.getLocationText()).thenReturn("후보 장소");
        when(revision.getOperatingHoursText()).thenReturn("후보 운영 시간");
        when(revision.getContactText()).thenReturn("055-000-0000");
        when(revision.getPrecautions()).thenReturn("후보 유의사항");
        when(revision.getAgeRequirement()).thenReturn("후보 연령 조건");
        when(revision.getMaterials()).thenReturn("후보 준비물");
        when(revision.getCancellationPolicyText()).thenReturn("후보 취소 규정");
        when(revision.getReservationPrice()).thenReturn(20_000L);
        when(revision.getSubmittedAt()).thenReturn(SUBMITTED_AT);
        when(candidateImage.isScopedTo(REGION_ID)).thenReturn(true);

        if (status == ContentRevisionStatus.EDIT_APPROVED) {
            when(revision.getReviewedAt()).thenReturn(REVIEWED_AT);
            when(revision.getReviewedBy()).thenReturn(reviewer);
        } else if (status == ContentRevisionStatus.EDIT_REJECTED) {
            when(revision.getReviewedAt()).thenReturn(REVIEWED_AT);
            when(revision.getReviewedBy()).thenReturn(reviewer);
            when(revision.getReviewReason()).thenReturn("대표 이미지를 보완해 주세요.");
        }
        return new RevisionFixture(content, revision, operator, region, candidateImage);
    }

    private record RevisionFixture(
        Content content,
        ContentRevision revision,
        AppUser operator,
        Region region,
        ImageObject candidateImage
    ) {
    }
}
