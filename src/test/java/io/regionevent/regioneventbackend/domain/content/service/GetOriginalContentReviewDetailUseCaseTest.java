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
import io.regionevent.regioneventbackend.domain.content.entity.ContentLog;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.image.entity.ImageObject;
import io.regionevent.regioneventbackend.domain.image.service.RepresentativeImageViewUrl;
import io.regionevent.regioneventbackend.domain.image.service.RepresentativeImageViewUrlService;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class GetOriginalContentReviewDetailUseCaseTest {

    private static final Long USER_ID = 1L;
    private static final Long REGION_ID = 10L;
    private static final Long CONTENT_ID = 101L;
    private static final Instant IMAGE_EXPIRES_AT = Instant.parse("2026-08-01T00:05:00Z");

    private final RegionAdminAuthorizationService regionAdminAuthorizationService =
        mock(RegionAdminAuthorizationService.class);
    private final OriginalContentReviewTargetService originalContentReviewTargetService =
        mock(OriginalContentReviewTargetService.class);
    private final ContentSessionService contentSessionService = mock(ContentSessionService.class);
    private final RepresentativeImageViewUrlService representativeImageViewUrlService =
        mock(RepresentativeImageViewUrlService.class);
    private final GetOriginalContentReviewDetailUseCase useCase = new GetOriginalContentReviewDetailUseCase(
        regionAdminAuthorizationService,
        originalContentReviewTargetService,
        contentSessionService,
        representativeImageViewUrlService
    );

    @Test
    void 최초_심사_대기_콘텐츠_상세와_PENDING_회차를_조립하고_인가_후에만_이미지_URL을_발급한다() {
        Content content = content(REGION_ID);
        ContentSession session = session();
        when(regionAdminAuthorizationService.requireAuthorizedRegionId(USER_ID)).thenReturn(REGION_ID);
        when(originalContentReviewTargetService.findByContentId(CONTENT_ID))
            .thenReturn(Optional.of(target(content, OriginalContentReviewTargetType.INITIAL_SUBMISSION)));
        when(contentSessionService.findPendingByContentId(CONTENT_ID)).thenReturn(List.of(session));
        when(representativeImageViewUrlService.createViewUrl(content.getRepresentativeImageObject()))
            .thenReturn(new RepresentativeImageViewUrl("https://example.invalid/view", IMAGE_EXPIRES_AT));

        OriginalContentReviewDetailResult result = useCase.get(USER_ID, CONTENT_ID);

        assertThat(result.contentId()).isEqualTo(CONTENT_ID);
        assertThat(result.regionId()).isEqualTo(REGION_ID);
        assertThat(result.operatorId()).isEqualTo(41L);
        assertThat(result.contentType()).isEqualTo(ContentType.EVENT_EXPERIENCE);
        assertThat(result.status()).isEqualTo(ContentStatus.PENDING);
        assertThat(result.representativeImageUrl()).isEqualTo("https://example.invalid/view");
        assertThat(result.representativeImageUrlExpiresAt()).isEqualTo(IMAGE_EXPIRES_AT);
        assertThat(result.sessions()).singleElement()
            .satisfies(resultSession -> {
                assertThat(resultSession.sessionId()).isEqualTo(701L);
                assertThat(resultSession.status()).isEqualTo(ContentSessionStatus.PENDING);
            });

        InOrder inOrder = inOrder(
            regionAdminAuthorizationService,
            originalContentReviewTargetService,
            contentSessionService,
            representativeImageViewUrlService
        );
        inOrder.verify(regionAdminAuthorizationService).requireAuthorizedRegionId(USER_ID);
        inOrder.verify(originalContentReviewTargetService).findByContentId(CONTENT_ID);
        inOrder.verify(contentSessionService).findPendingByContentId(CONTENT_ID);
        inOrder.verify(representativeImageViewUrlService).createViewUrl(content.getRepresentativeImageObject());
    }

    @Test
    void 다른_담당_지역의_원본_심사_대기_콘텐츠는_URL을_발급하지_않고_거부한다() {
        Content content = content(20L);
        when(regionAdminAuthorizationService.requireAuthorizedRegionId(USER_ID)).thenReturn(REGION_ID);
        when(originalContentReviewTargetService.findByContentId(CONTENT_ID))
            .thenReturn(Optional.of(target(content, OriginalContentReviewTargetType.INITIAL_SUBMISSION)));

        assertThatThrownBy(() -> useCase.get(USER_ID, CONTENT_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN)
            );

        verifyNoInteractions(contentSessionService, representativeImageViewUrlService);
    }

    @Test
    void 공개_전_수정_심사_대기_콘텐츠는_NOT_FOUND로_비노출한다() {
        Content content = content(REGION_ID);
        when(regionAdminAuthorizationService.requireAuthorizedRegionId(USER_ID)).thenReturn(REGION_ID);
        when(originalContentReviewTargetService.findByContentId(CONTENT_ID))
            .thenReturn(Optional.of(target(content, OriginalContentReviewTargetType.PRE_PUBLICATION_REVISION)));

        assertThatThrownBy(() -> useCase.get(USER_ID, CONTENT_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND)
            );

        verifyNoInteractions(contentSessionService, representativeImageViewUrlService);
    }

    @Test
    void 현재_대표_이미지가_지역과_일치하지_않으면_URL을_발급하지_않는다() {
        Content content = content(REGION_ID);
        when(content.getRepresentativeImageObject().isScopedTo(REGION_ID)).thenReturn(false);
        when(regionAdminAuthorizationService.requireAuthorizedRegionId(USER_ID)).thenReturn(REGION_ID);
        when(originalContentReviewTargetService.findByContentId(CONTENT_ID))
            .thenReturn(Optional.of(target(content, OriginalContentReviewTargetType.RESUBMISSION_AFTER_REJECTION)));

        assertThatThrownBy(() -> useCase.get(USER_ID, CONTENT_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR)
            );

        verifyNoInteractions(contentSessionService, representativeImageViewUrlService);
    }

    private Content content(Long regionId) {
        Content content = mock(Content.class);
        Region region = mock(Region.class);
        AppUser operator = mock(AppUser.class);
        ImageObject representativeImageObject = mock(ImageObject.class);
        when(content.getContentId()).thenReturn(CONTENT_ID);
        when(content.getRegion()).thenReturn(region);
        when(content.getOperator()).thenReturn(operator);
        when(content.getContentType()).thenReturn(ContentType.EVENT_EXPERIENCE);
        when(content.getStatus()).thenReturn(ContentStatus.PENDING);
        when(content.getTitle()).thenReturn("가야 문화 체험");
        when(content.getDescription()).thenReturn("가야 문화를 체험하는 행사입니다.");
        when(content.getLocationText()).thenReturn("김해문화의전당");
        when(content.getOperatingHoursText()).thenReturn("매주 토요일 10:00~16:00");
        when(content.getContactText()).thenReturn("055-000-0000");
        when(content.getPrecautions()).thenReturn("편한 복장으로 참여해 주세요.");
        when(content.getAgeRequirement()).thenReturn("초등학생 이상");
        when(content.getMaterials()).thenReturn("필기도구");
        when(content.getCancellationPolicyText()).thenReturn("회차 시작 전까지 취소할 수 있습니다.");
        when(content.getPublishAt()).thenReturn(Instant.parse("2026-08-20T00:00:00Z"));
        when(content.getRepresentativeImageObject()).thenReturn(representativeImageObject);
        when(content.getRepresentativeImageAssignedAt()).thenReturn(Instant.parse("2026-08-01T00:00:00Z"));
        when(region.getRegionId()).thenReturn(regionId);
        when(operator.getUserId()).thenReturn(41L);
        when(representativeImageObject.isScopedTo(REGION_ID)).thenReturn(true);
        return content;
    }

    private OriginalContentReviewTarget target(
        Content content,
        OriginalContentReviewTargetType type
    ) {
        return new OriginalContentReviewTarget(content, mock(ContentLog.class), null, type);
    }

    private ContentSession session() {
        ContentSession session = mock(ContentSession.class);
        when(session.getSessionId()).thenReturn(701L);
        when(session.getStatus()).thenReturn(ContentSessionStatus.PENDING);
        when(session.getStartsAt()).thenReturn(Instant.parse("2026-08-02T01:00:00Z"));
        when(session.getEndsAt()).thenReturn(Instant.parse("2026-08-02T03:00:00Z"));
        when(session.getCheckinOpenAt()).thenReturn(Instant.parse("2026-08-02T00:30:00Z"));
        when(session.getCheckinCloseAt()).thenReturn(Instant.parse("2026-08-02T02:30:00Z"));
        when(session.getCapacity()).thenReturn(20);
        when(session.getRemainingCapacity()).thenReturn(20);
        return session;
    }
}
