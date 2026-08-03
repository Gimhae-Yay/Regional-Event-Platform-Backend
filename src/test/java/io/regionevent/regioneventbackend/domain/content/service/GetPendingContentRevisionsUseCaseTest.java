package io.regionevent.regioneventbackend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevision;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.image.entity.ImageLifecycleStatus;
import io.regionevent.regioneventbackend.domain.image.entity.ImageObject;
import io.regionevent.regioneventbackend.domain.image.service.RepresentativeImageViewUrl;
import io.regionevent.regioneventbackend.domain.image.service.RepresentativeImageViewUrlService;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class GetPendingContentRevisionsUseCaseTest {

    private static final Long USER_ID = 1L;
    private static final Long REGION_ID = 10L;
    private static final Instant SUBMITTED_AT = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant IMAGE_EXPIRES_AT = Instant.parse("2026-08-01T00:05:00Z");

    private final RegionAdminAuthorizationService regionAdminAuthorizationService =
        mock(RegionAdminAuthorizationService.class);
    private final ContentRevisionService contentRevisionService = mock(ContentRevisionService.class);
    private final OriginalContentReviewTargetService originalContentReviewTargetService =
        mock(OriginalContentReviewTargetService.class);
    private final ContentRevisionReviewTypePolicy contentRevisionReviewTypePolicy =
        mock(ContentRevisionReviewTypePolicy.class);
    private final RepresentativeImageViewUrlService representativeImageViewUrlService =
        mock(RepresentativeImageViewUrlService.class);
    private final GetPendingContentRevisionsUseCase useCase = new GetPendingContentRevisionsUseCase(
        regionAdminAuthorizationService,
        contentRevisionService,
        originalContentReviewTargetService,
        contentRevisionReviewTypePolicy,
        representativeImageViewUrlService
    );

    @Test
    void 공개와_공개_전_수정본을_고정_순서대로_조립한다() {
        ContentRevisionReviewCandidate published = candidate(
            501L,
            101L,
            ContentStatus.PUBLISHED,
            null,
            SUBMITTED_AT
        );
        ContentRevisionReviewCandidate prePublic = candidate(
            502L,
            102L,
            ContentStatus.PENDING,
            Instant.parse("2026-08-20T00:00:00Z"),
            SUBMITTED_AT.plusSeconds(1)
        );
        OriginalContentReviewTarget target = mock(OriginalContentReviewTarget.class);
        when(target.type()).thenReturn(OriginalContentReviewTargetType.PRE_PUBLICATION_REVISION);
        when(regionAdminAuthorizationService.requireAuthorizedRegionId(USER_ID)).thenReturn(REGION_ID);
        when(contentRevisionService.findReviewCandidatesByRegionId(REGION_ID))
            .thenReturn(List.of(published, prePublic));
        when(originalContentReviewTargetService.findByContentId(101L)).thenReturn(Optional.empty());
        when(originalContentReviewTargetService.findByContentId(102L)).thenReturn(Optional.of(target));
        when(contentRevisionReviewTypePolicy.classify(published, false))
            .thenReturn(ContentRevisionReviewType.PUBLISHED_REVISION);
        when(contentRevisionReviewTypePolicy.classify(prePublic, true))
            .thenReturn(ContentRevisionReviewType.PRE_PUBLIC_REVISION);
        when(representativeImageViewUrlService.createViewUrl(published.candidateImageObject()))
            .thenReturn(new RepresentativeImageViewUrl("https://example.invalid/view/1", IMAGE_EXPIRES_AT));
        when(representativeImageViewUrlService.createViewUrl(prePublic.candidateImageObject()))
            .thenReturn(new RepresentativeImageViewUrl(
                "https://example.invalid/view/2",
                IMAGE_EXPIRES_AT.plusSeconds(1)
            ));

        PendingContentRevisionListResult result = useCase.get(USER_ID, "EDIT_REQUESTED");

        assertThat(result.revisions()).extracting(PendingContentRevisionListResult.Revision::revisionId)
            .containsExactly(501L, 502L);
        assertThat(result.revisions().get(0).reviewType())
            .isEqualTo(ContentRevisionReviewType.PUBLISHED_REVISION);
        assertThat(result.revisions().get(0).candidatePublishAt()).isNull();
        assertThat(result.revisions().get(1).reviewType())
            .isEqualTo(ContentRevisionReviewType.PRE_PUBLIC_REVISION);
        assertThat(result.revisions().get(1).candidatePublishAt())
            .isEqualTo(Instant.parse("2026-08-20T00:00:00Z"));
        assertThat(result.revisions().get(0).operatorId()).isEqualTo(20L);
        assertThat(result.revisions().get(0).operatorName()).isEqualTo("테스트 운영자");

        InOrder inOrder = inOrder(
            regionAdminAuthorizationService,
            contentRevisionService,
            contentRevisionReviewTypePolicy,
            representativeImageViewUrlService
        );
        inOrder.verify(regionAdminAuthorizationService).requireAuthorizedRegionId(USER_ID);
        inOrder.verify(contentRevisionService).findReviewCandidatesByRegionId(REGION_ID);
        inOrder.verify(contentRevisionReviewTypePolicy).classify(published, false);
        inOrder.verify(contentRevisionReviewTypePolicy).classify(prePublic, true);
        inOrder.verify(representativeImageViewUrlService).createViewUrl(published.candidateImageObject());
        inOrder.verify(representativeImageViewUrlService).createViewUrl(prePublic.candidateImageObject());
    }

    @Test
    void 심사_대기_수정본이_없으면_빈_목록을_반환한다() {
        when(regionAdminAuthorizationService.requireAuthorizedRegionId(USER_ID)).thenReturn(REGION_ID);
        when(contentRevisionService.findReviewCandidatesByRegionId(REGION_ID)).thenReturn(List.of());

        PendingContentRevisionListResult result = useCase.get(USER_ID, "EDIT_REQUESTED");

        assertThat(result.revisions()).isEmpty();
        verifyNoInteractions(
            originalContentReviewTargetService,
            contentRevisionReviewTypePolicy,
            representativeImageViewUrlService
        );
    }

    @Test
    void status가_없거나_EDIT_REQUESTED가_아니면_인가_후_INVALID_INPUT으로_거부한다() {
        when(regionAdminAuthorizationService.requireAuthorizedRegionId(USER_ID)).thenReturn(REGION_ID);

        for (String status : new String[] {null, "PENDING", "edit_requested"}) {
            assertThatThrownBy(() -> useCase.get(USER_ID, status))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT)
                );
        }
        verifyNoInteractions(
            contentRevisionService,
            originalContentReviewTargetService,
            contentRevisionReviewTypePolicy,
            representativeImageViewUrlService
        );
    }

    @Test
    void 한_행의_상태_정합성이_깨지면_어떤_이미지_URL도_발급하지_않는다() {
        ContentRevisionReviewCandidate validCandidate = candidate(
            501L,
            101L,
            ContentStatus.PUBLISHED,
            null,
            SUBMITTED_AT
        );
        ContentRevisionReviewCandidate invalidCandidate = candidate(
            502L,
            102L,
            ContentStatus.PENDING,
            null,
            SUBMITTED_AT.plusSeconds(1)
        );
        when(regionAdminAuthorizationService.requireAuthorizedRegionId(USER_ID)).thenReturn(REGION_ID);
        when(contentRevisionService.findReviewCandidatesByRegionId(REGION_ID))
            .thenReturn(List.of(validCandidate, invalidCandidate));
        when(originalContentReviewTargetService.findByContentId(101L)).thenReturn(Optional.empty());
        when(originalContentReviewTargetService.findByContentId(102L)).thenReturn(Optional.empty());
        when(contentRevisionReviewTypePolicy.classify(validCandidate, false))
            .thenReturn(ContentRevisionReviewType.PUBLISHED_REVISION);
        when(contentRevisionReviewTypePolicy.classify(invalidCandidate, false))
            .thenThrow(new IllegalStateException("invalid review state"));

        assertThatThrownBy(() -> useCase.get(USER_ID, "EDIT_REQUESTED"))
            .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(representativeImageViewUrlService);
    }

    @Test
    void 한_행의_후보_이미지가_직접_연결된_ACTIVE가_아니면_전체를_거부한다() {
        ContentRevisionReviewCandidate validCandidate = candidate(
            501L,
            101L,
            ContentStatus.PUBLISHED,
            null,
            SUBMITTED_AT
        );
        ContentRevisionReviewCandidate invalidCandidate = candidate(
            502L,
            102L,
            ContentStatus.PUBLISHED,
            null,
            SUBMITTED_AT.plusSeconds(1)
        );
        when(invalidCandidate.candidateImageObject().getLifecycleStatus())
            .thenReturn(ImageLifecycleStatus.DELETE_PENDING);
        when(regionAdminAuthorizationService.requireAuthorizedRegionId(USER_ID)).thenReturn(REGION_ID);
        when(contentRevisionService.findReviewCandidatesByRegionId(REGION_ID))
            .thenReturn(List.of(validCandidate, invalidCandidate));
        when(originalContentReviewTargetService.findByContentId(101L)).thenReturn(Optional.empty());
        when(originalContentReviewTargetService.findByContentId(102L)).thenReturn(Optional.empty());
        when(contentRevisionReviewTypePolicy.classify(validCandidate, false))
            .thenReturn(ContentRevisionReviewType.PUBLISHED_REVISION);
        when(contentRevisionReviewTypePolicy.classify(invalidCandidate, false))
            .thenReturn(ContentRevisionReviewType.PUBLISHED_REVISION);

        assertThatThrownBy(() -> useCase.get(USER_ID, "EDIT_REQUESTED"))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR)
            );
        verifyNoInteractions(representativeImageViewUrlService);
    }

    private ContentRevisionReviewCandidate candidate(
        Long revisionId,
        Long contentId,
        ContentStatus contentStatus,
        Instant candidatePublishAt,
        Instant submittedAt
    ) {
        ContentRevision revision = mock(ContentRevision.class);
        Content content = mock(Content.class);
        Region region = mock(Region.class);
        AppUser operator = mock(AppUser.class);
        ImageObject imageObject = mock(ImageObject.class);
        when(revision.getContentRevisionId()).thenReturn(revisionId);
        when(revision.getTitle()).thenReturn("수정본 제목 " + revisionId);
        when(revision.getPublishAt()).thenReturn(candidatePublishAt);
        when(revision.getSubmittedAt()).thenReturn(submittedAt);
        when(revision.getCandidateImageAssignedAt()).thenReturn(submittedAt);
        when(content.getContentId()).thenReturn(contentId);
        when(content.getStatus()).thenReturn(contentStatus);
        when(content.getRegion()).thenReturn(region);
        when(region.getRegionId()).thenReturn(REGION_ID);
        when(operator.getUserId()).thenReturn(20L);
        when(operator.getName()).thenReturn("테스트 운영자");
        when(imageObject.getLifecycleStatus()).thenReturn(ImageLifecycleStatus.ACTIVE);
        when(imageObject.getLinkedAt()).thenReturn(submittedAt);
        when(imageObject.isScopedTo(REGION_ID)).thenReturn(true);
        return new ContentRevisionReviewCandidate(revision, content, operator, imageObject);
    }
}
