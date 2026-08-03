package io.regionevent.regioneventbackend.domain.content.service;

import static org.junit.jupiter.api.Assertions.assertAll;

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
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.image.entity.ImageObject;
import io.regionevent.regioneventbackend.domain.image.service.RepresentativeImageViewUrl;
import io.regionevent.regioneventbackend.domain.image.service.RepresentativeImageViewUrlService;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class GetContentRevisionReviewDetailUseCaseTest {

    private static final Long USER_ID = 1L;
    private static final Long REGION_ID = 10L;
    private static final Long CONTENT_ID = 101L;
    private static final Long REVISION_ID = 501L;
    private static final Instant SUBMITTED_AT = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant IMAGE_EXPIRES_AT = Instant.parse("2026-08-01T00:05:00Z");

    private final RegionAdminAuthorizationService regionAdminAuthorizationService =
        mock(RegionAdminAuthorizationService.class);
    private final ContentRevisionService contentRevisionService = mock(ContentRevisionService.class);
    private final OriginalContentReviewTargetService originalContentReviewTargetService =
        mock(OriginalContentReviewTargetService.class);
    private final ContentRevisionReviewTypePolicy contentRevisionReviewTypePolicy =
        mock(ContentRevisionReviewTypePolicy.class);
    private final ContentSessionService contentSessionService = mock(ContentSessionService.class);
    private final RepresentativeImageViewUrlService representativeImageViewUrlService =
        mock(RepresentativeImageViewUrlService.class);
    private final GetContentRevisionReviewDetailUseCase useCase = new GetContentRevisionReviewDetailUseCase(
        regionAdminAuthorizationService,
        contentRevisionService,
        originalContentReviewTargetService,
        contentRevisionReviewTypePolicy,
        contentSessionService,
        representativeImageViewUrlService
    );

    @Test
    void 전체_단위_계약을_보존한다() {
        assertAll(
            () -> new GetContentRevisionReviewDetailUseCaseTest().공개_콘텐츠_수정본_상세를_조립하고_인가_후에만_이미지_URL을_발급한다(),
            () -> new GetContentRevisionReviewDetailUseCaseTest().다른_담당_지역의_수정본은_URL을_발급하지_않고_거부한다(),
            () -> new GetContentRevisionReviewDetailUseCaseTest().후보_이미지_지역_연결이_일치하지_않으면_URL을_발급하지_않는다(),
            () -> new GetContentRevisionReviewDetailUseCaseTest().원본과_후보_공개시각과_이력_상태_조합이_잘못되면_URL을_발급하지_않는다()
        );
    }

    void 공개_콘텐츠_수정본_상세를_조립하고_인가_후에만_이미지_URL을_발급한다() {
        ContentRevisionReviewCandidate candidate = candidate(REGION_ID);
        ContentSession session = session();
        when(regionAdminAuthorizationService.requireAuthorizedRegionId(USER_ID)).thenReturn(REGION_ID);
        when(contentRevisionService.findReviewCandidateById(REVISION_ID)).thenReturn(candidate);
        when(originalContentReviewTargetService.findByContentId(CONTENT_ID)).thenReturn(Optional.empty());
        when(contentRevisionReviewTypePolicy.classify(candidate, false))
            .thenReturn(ContentRevisionReviewType.PUBLISHED_REVISION);
        when(contentSessionService.findCurrentSessionsByContentId(CONTENT_ID)).thenReturn(List.of(session));
        when(representativeImageViewUrlService.createViewUrl(candidate.candidateImageObject()))
            .thenReturn(new RepresentativeImageViewUrl("https://example.invalid/view", IMAGE_EXPIRES_AT));

        ContentRevisionReviewDetailResult result = useCase.get(USER_ID, REVISION_ID);

        assertThat(result.revisionId()).isEqualTo(REVISION_ID);
        assertThat(result.contentId()).isEqualTo(CONTENT_ID);
        assertThat(result.reviewType()).isEqualTo(ContentRevisionReviewType.PUBLISHED_REVISION);
        assertThat(result.contentStatus()).isEqualTo(ContentStatus.PUBLISHED);
        assertThat(result.representativeImageUrl()).isEqualTo("https://example.invalid/view");
        assertThat(result.representativeImageUrlExpiresAt()).isEqualTo(IMAGE_EXPIRES_AT);
        assertThat(result.candidatePublishAt()).isNull();
        assertThat(result.sessions()).singleElement()
            .satisfies(resultSession -> {
                assertThat(resultSession.sessionId()).isEqualTo(701L);
                assertThat(resultSession.status()).isEqualTo(ContentSessionStatus.SCHEDULED);
            });

        InOrder inOrder = inOrder(
            regionAdminAuthorizationService,
            contentRevisionService,
            representativeImageViewUrlService
        );
        inOrder.verify(regionAdminAuthorizationService).requireAuthorizedRegionId(USER_ID);
        inOrder.verify(contentRevisionService).findReviewCandidateById(REVISION_ID);
        inOrder.verify(representativeImageViewUrlService).createViewUrl(candidate.candidateImageObject());
    }

    void 다른_담당_지역의_수정본은_URL을_발급하지_않고_거부한다() {
        ContentRevisionReviewCandidate candidate = candidate(20L);
        when(regionAdminAuthorizationService.requireAuthorizedRegionId(USER_ID)).thenReturn(REGION_ID);
        when(contentRevisionService.findReviewCandidateById(REVISION_ID)).thenReturn(candidate);

        assertThatThrownBy(() -> useCase.get(USER_ID, REVISION_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN)
            );
        verifyNoInteractions(
            originalContentReviewTargetService,
            contentRevisionReviewTypePolicy,
            contentSessionService,
            representativeImageViewUrlService
        );
    }

    void 후보_이미지_지역_연결이_일치하지_않으면_URL을_발급하지_않는다() {
        ContentRevisionReviewCandidate candidate = candidate(REGION_ID);
        when(candidate.candidateImageObject().isScopedTo(REGION_ID)).thenReturn(false);
        when(regionAdminAuthorizationService.requireAuthorizedRegionId(USER_ID)).thenReturn(REGION_ID);
        when(contentRevisionService.findReviewCandidateById(REVISION_ID)).thenReturn(candidate);
        when(originalContentReviewTargetService.findByContentId(CONTENT_ID)).thenReturn(Optional.empty());
        when(contentRevisionReviewTypePolicy.classify(candidate, false))
            .thenReturn(ContentRevisionReviewType.PUBLISHED_REVISION);

        assertThatThrownBy(() -> useCase.get(USER_ID, REVISION_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR)
            );
        verifyNoInteractions(contentSessionService, representativeImageViewUrlService);
    }

    void 원본과_후보_공개시각과_이력_상태_조합이_잘못되면_URL을_발급하지_않는다() {
        ContentRevisionReviewCandidate candidate = candidate(REGION_ID);
        when(regionAdminAuthorizationService.requireAuthorizedRegionId(USER_ID)).thenReturn(REGION_ID);
        when(contentRevisionService.findReviewCandidateById(REVISION_ID)).thenReturn(candidate);
        when(originalContentReviewTargetService.findByContentId(CONTENT_ID)).thenReturn(Optional.empty());
        when(contentRevisionReviewTypePolicy.classify(candidate, false))
            .thenThrow(new IllegalStateException("invalid review state"));

        assertThatThrownBy(() -> useCase.get(USER_ID, REVISION_ID))
            .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(contentSessionService, representativeImageViewUrlService);
    }

    private ContentRevisionReviewCandidate candidate(Long regionId) {
        ContentRevision revision = mock(ContentRevision.class);
        Content content = mock(Content.class);
        Region region = mock(Region.class);
        AppUser operator = mock(AppUser.class);
        ImageObject candidateImageObject = mock(ImageObject.class);
        when(revision.getContentRevisionId()).thenReturn(REVISION_ID);
        when(revision.getTitle()).thenReturn("수정본 제목");
        when(revision.getDescription()).thenReturn("수정본 설명");
        when(revision.getLocationText()).thenReturn("김해문화의전당");
        when(revision.getOperatingHoursText()).thenReturn("매주 토요일 10:00~16:00");
        when(revision.getContactText()).thenReturn("055-000-0000");
        when(revision.getPrecautions()).thenReturn("안내를 따라주세요.");
        when(revision.getAgeRequirement()).thenReturn("초등학생 이상");
        when(revision.getMaterials()).thenReturn("필기도구");
        when(revision.getCancellationPolicyText()).thenReturn("회차 시작 전까지 취소할 수 있습니다.");
        when(revision.getCandidateImageAssignedAt()).thenReturn(SUBMITTED_AT);
        when(revision.getSubmittedAt()).thenReturn(SUBMITTED_AT);
        when(content.getContentId()).thenReturn(CONTENT_ID);
        when(content.getRegion()).thenReturn(region);
        when(content.getStatus()).thenReturn(ContentStatus.PUBLISHED);
        when(region.getRegionId()).thenReturn(regionId);
        when(candidateImageObject.isScopedTo(REGION_ID)).thenReturn(true);
        return new ContentRevisionReviewCandidate(revision, content, operator, candidateImageObject);
    }

    private ContentSession session() {
        ContentSession session = mock(ContentSession.class);
        when(session.getSessionId()).thenReturn(701L);
        when(session.getStatus()).thenReturn(ContentSessionStatus.SCHEDULED);
        when(session.getStartsAt()).thenReturn(Instant.parse("2026-08-02T01:00:00Z"));
        when(session.getEndsAt()).thenReturn(Instant.parse("2026-08-02T03:00:00Z"));
        when(session.getCheckinOpenAt()).thenReturn(Instant.parse("2026-08-02T00:30:00Z"));
        when(session.getCheckinCloseAt()).thenReturn(Instant.parse("2026-08-02T02:30:00Z"));
        when(session.getCapacity()).thenReturn(20);
        when(session.getRemainingCapacity()).thenReturn(10);
        return session;
    }
}
