package io.regionevent.regioneventbackend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLog;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentType;
import io.regionevent.regioneventbackend.domain.image.entity.ImageLifecycleStatus;
import io.regionevent.regioneventbackend.domain.image.entity.ImageObject;
import io.regionevent.regioneventbackend.domain.image.service.RepresentativeImageViewUrl;
import io.regionevent.regioneventbackend.domain.image.service.RepresentativeImageViewUrlService;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class GetPendingContentsUseCaseTest {

    private static final Long USER_ID = 1L;
    private static final Long REGION_ID = 10L;
    private static final Instant SUBMITTED_AT = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant IMAGE_EXPIRES_AT = Instant.parse("2026-08-01T00:05:00Z");

    private final RegionAdminAuthorizationService regionAdminAuthorizationService =
        mock(RegionAdminAuthorizationService.class);
    private final ContentService contentService = mock(ContentService.class);
    private final OriginalContentReviewTargetService originalContentReviewTargetService =
        mock(OriginalContentReviewTargetService.class);
    private final RepresentativeImageViewUrlService representativeImageViewUrlService =
        mock(RepresentativeImageViewUrlService.class);
    private final GetPendingContentsUseCase useCase = new GetPendingContentsUseCase(
        regionAdminAuthorizationService,
        contentService,
        originalContentReviewTargetService,
        representativeImageViewUrlService
    );

    @Test
    void 최초_심사와_재제출을_제출시각과_ID_오름차순으로_반환하고_공개전_수정심사는_제외한다() {
        OriginalContentReviewTarget laterInitialTarget = target(
            102L,
            SUBMITTED_AT.plusSeconds(1),
            true
        );
        OriginalContentReviewTarget firstResubmissionTarget = target(
            101L,
            SUBMITTED_AT,
            true
        );
        OriginalContentReviewTarget tiedInitialTarget = target(
            100L,
            SUBMITTED_AT,
            true
        );
        OriginalContentReviewTarget prePublicationRevisionTarget = target(
            99L,
            SUBMITTED_AT.minusSeconds(1),
            false
        );
        Content laterInitialContent = laterInitialTarget.content();
        Content firstResubmissionContent = firstResubmissionTarget.content();
        Content tiedInitialContent = tiedInitialTarget.content();
        Content prePublicationRevisionContent = prePublicationRevisionTarget.content();
        ImageObject firstResubmissionImage = firstResubmissionContent.getRepresentativeImageObject();
        ImageObject tiedInitialImage = tiedInitialContent.getRepresentativeImageObject();
        ImageObject laterInitialImage = laterInitialContent.getRepresentativeImageObject();
        when(regionAdminAuthorizationService.requireAuthorizedRegionId(USER_ID)).thenReturn(REGION_ID);
        when(contentService.findPendingReviewContentsByRegionId(REGION_ID)).thenReturn(List.of(
            laterInitialContent,
            firstResubmissionContent,
            tiedInitialContent,
            prePublicationRevisionContent
        ));
        when(originalContentReviewTargetService.findByContents(anyList())).thenReturn(List.of(
            laterInitialTarget,
            firstResubmissionTarget,
            tiedInitialTarget,
            prePublicationRevisionTarget
        ));
        when(representativeImageViewUrlService.createViewUrl(tiedInitialImage)).thenReturn(new RepresentativeImageViewUrl(
            "https://example.invalid/view/100",
            IMAGE_EXPIRES_AT.minusSeconds(1)
        ));
        when(representativeImageViewUrlService.createViewUrl(firstResubmissionImage)).thenReturn(new RepresentativeImageViewUrl(
            "https://example.invalid/view/101",
            IMAGE_EXPIRES_AT
        ));
        when(representativeImageViewUrlService.createViewUrl(laterInitialImage)).thenReturn(new RepresentativeImageViewUrl(
            "https://example.invalid/view/102",
            IMAGE_EXPIRES_AT.plusSeconds(1)
        ));

        PendingContentListResult result = useCase.get(USER_ID, "PENDING");

        assertThat(result.contents()).extracting(PendingContentListResult.Content::contentId)
            .containsExactly(100L, 101L, 102L);
        assertThat(result.contents()).extracting(PendingContentListResult.Content::submittedAt)
            .containsExactly(SUBMITTED_AT, SUBMITTED_AT, SUBMITTED_AT.plusSeconds(1));
        assertThat(result.contents().getFirst().operatorName()).isEqualTo("운영자 100");
        assertThat(result.contents().getFirst().representativeImageUrl())
            .isEqualTo("https://example.invalid/view/100");
    }

    @Test
    void 심사_대기_콘텐츠가_없으면_빈_목록을_반환하고_URL을_발급하지_않는다() {
        when(regionAdminAuthorizationService.requireAuthorizedRegionId(USER_ID)).thenReturn(REGION_ID);
        when(contentService.findPendingReviewContentsByRegionId(REGION_ID)).thenReturn(List.of());
        when(originalContentReviewTargetService.findByContents(List.of())).thenReturn(List.of());

        PendingContentListResult result = useCase.get(USER_ID, "PENDING");

        assertThat(result.contents()).isEmpty();
        verifyNoInteractions(representativeImageViewUrlService);
    }

    @Test
    void status가_없거나_PENDING이_아니면_인가_후_입력오류로_거부한다() {
        when(regionAdminAuthorizationService.requireAuthorizedRegionId(USER_ID)).thenReturn(REGION_ID);

        for (String status : new String[] {null, "APPROVED", "pending"}) {
            assertThatThrownBy(() -> useCase.get(USER_ID, status))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT)
                );
        }
        verifyNoInteractions(
            contentService,
            originalContentReviewTargetService,
            representativeImageViewUrlService
        );
    }

    @Test
    void 목록의_대표이미지_정합성이_깨지면_어떤_URL도_발급하지_않는다() {
        OriginalContentReviewTarget validTarget = target(101L, SUBMITTED_AT, true);
        OriginalContentReviewTarget invalidTarget = target(102L, SUBMITTED_AT.plusSeconds(1), true);
        Content validContent = validTarget.content();
        Content invalidContent = invalidTarget.content();
        ImageObject invalidImage = invalidContent.getRepresentativeImageObject();
        when(invalidImage.getLifecycleStatus())
            .thenReturn(ImageLifecycleStatus.DELETE_PENDING);
        when(regionAdminAuthorizationService.requireAuthorizedRegionId(USER_ID)).thenReturn(REGION_ID);
        when(contentService.findPendingReviewContentsByRegionId(REGION_ID)).thenReturn(List.of(
            validContent,
            invalidContent
        ));
        when(originalContentReviewTargetService.findByContents(anyList())).thenReturn(List.of(
            validTarget,
            invalidTarget
        ));

        assertThatThrownBy(() -> useCase.get(USER_ID, "PENDING"))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR)
            );
        verifyNoInteractions(representativeImageViewUrlService);
    }

    private OriginalContentReviewTarget target(
        Long contentId,
        Instant submittedAt,
        boolean originalReviewTarget
    ) {
        Content content = mock(Content.class);
        ContentLog pendingLog = mock(ContentLog.class);
        AppUser operator = mock(AppUser.class);
        ImageObject representativeImageObject = mock(ImageObject.class);
        OriginalContentReviewTarget target = mock(OriginalContentReviewTarget.class);
        when(content.getContentId()).thenReturn(contentId);
        when(content.getDeletedAt()).thenReturn(null);
        when(content.getStatus()).thenReturn(ContentStatus.PENDING);
        when(content.isScopedTo(REGION_ID)).thenReturn(true);
        when(content.getContentType()).thenReturn(ContentType.EVENT_EXPERIENCE);
        when(content.getTitle()).thenReturn("콘텐츠 " + contentId);
        when(content.getPublishAt()).thenReturn(Instant.parse("2026-08-10T00:00:00Z"));
        when(content.getOperator()).thenReturn(operator);
        when(content.getRepresentativeImageObject()).thenReturn(representativeImageObject);
        when(content.getRepresentativeImageAssignedAt()).thenReturn(submittedAt);
        when(operator.getUserId()).thenReturn(contentId + 100L);
        when(operator.getName()).thenReturn("운영자 " + contentId);
        when(representativeImageObject.getLifecycleStatus()).thenReturn(ImageLifecycleStatus.ACTIVE);
        when(representativeImageObject.getLinkedAt()).thenReturn(submittedAt);
        when(representativeImageObject.isScopedTo(REGION_ID)).thenReturn(true);
        when(pendingLog.getDate()).thenReturn(submittedAt);
        when(target.content()).thenReturn(content);
        when(target.pendingLog()).thenReturn(pendingLog);
        when(target.isOriginalReviewTarget()).thenReturn(originalReviewTarget);
        return target;
    }
}
