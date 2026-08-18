package io.regionevent.regioneventbackend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLog;
import io.regionevent.regioneventbackend.domain.content.entity.ContentLogStatus;
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

class GetRegionAdminContentsUseCaseTest {

    private static final Long USER_ID = 1L;
    private static final Long REGION_ID = 10L;
    private static final Instant SUBMITTED_AT = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant IMAGE_EXPIRES_AT = Instant.parse("2026-08-01T00:05:00Z");

    private final RegionAdminAuthorizationService regionAdminAuthorizationService =
        mock(RegionAdminAuthorizationService.class);
    private final ContentService contentService = mock(ContentService.class);
    private final ContentLogService contentLogService = mock(ContentLogService.class);
    private final OriginalContentReviewTargetService originalContentReviewTargetService =
        mock(OriginalContentReviewTargetService.class);
    private final RepresentativeImageViewUrlService representativeImageViewUrlService =
        mock(RepresentativeImageViewUrlService.class);
    private final GetRegionAdminContentsUseCase useCase = new GetRegionAdminContentsUseCase(
        regionAdminAuthorizationService,
        contentService,
        contentLogService,
        originalContentReviewTargetService,
        representativeImageViewUrlService
    );

    @Test
    void PENDING은_최초심사와_재제출을_제출시각과_ID_오름차순으로_반환하고_공개전_수정심사는_제외한다() {
        OriginalContentReviewTarget laterInitialTarget = pendingTarget(
            102L,
            SUBMITTED_AT.plusSeconds(1),
            true
        );
        OriginalContentReviewTarget firstResubmissionTarget = pendingTarget(
            101L,
            SUBMITTED_AT,
            true
        );
        OriginalContentReviewTarget tiedInitialTarget = pendingTarget(100L, SUBMITTED_AT, true);
        OriginalContentReviewTarget prePublicationRevisionTarget = pendingTarget(
            99L,
            SUBMITTED_AT.minusSeconds(1),
            false
        );
        Content laterInitialContent = laterInitialTarget.content();
        Content firstResubmissionContent = firstResubmissionTarget.content();
        Content tiedInitialContent = tiedInitialTarget.content();
        Content prePublicationRevisionContent = prePublicationRevisionTarget.content();
        when(regionAdminAuthorizationService.requireAuthorizedRegionId(USER_ID)).thenReturn(REGION_ID);
        when(contentService.findContentsByRegionIdAndStatus(REGION_ID, ContentStatus.PENDING)).thenReturn(List.of(
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
        stubImageUrl(tiedInitialContent, 100L, IMAGE_EXPIRES_AT.minusSeconds(1));
        stubImageUrl(firstResubmissionContent, 101L, IMAGE_EXPIRES_AT);
        stubImageUrl(laterInitialContent, 102L, IMAGE_EXPIRES_AT.plusSeconds(1));

        RegionAdminContentListResult result = useCase.get(USER_ID, "PENDING");

        assertThat(result.status()).isEqualTo(ContentStatus.PENDING);
        assertThat(result.contents()).extracting(RegionAdminContentListResult.Content::contentId)
            .containsExactly(100L, 101L, 102L);
        assertThat(result.contents()).extracting(RegionAdminContentListResult.Content::submittedAt)
            .containsExactly(SUBMITTED_AT, SUBMITTED_AT, SUBMITTED_AT.plusSeconds(1));
        assertThat(result.contents()).extracting(RegionAdminContentListResult.Content::approvedAt)
            .containsOnlyNulls();
        assertThat(result.contents().getFirst().operatorName()).isEqualTo("운영자 100");
    }

    @Test
    void APPROVED는_공개예정시각과_ID_오름차순으로_승인시각만_반환한다() {
        Content laterContent = approvedContent(102L, SUBMITTED_AT.plusSeconds(120));
        Content tiedContent = approvedContent(101L, SUBMITTED_AT.plusSeconds(60));
        Content firstContent = approvedContent(100L, SUBMITTED_AT.plusSeconds(60));
        Map<Long, ContentLog> approvedLogs = Map.of(
            100L, statusLog(ContentLogStatus.APPROVED, SUBMITTED_AT.plusSeconds(10)),
            101L, statusLog(ContentLogStatus.APPROVED, SUBMITTED_AT.plusSeconds(20)),
            102L, statusLog(ContentLogStatus.APPROVED, SUBMITTED_AT.plusSeconds(30))
        );
        when(regionAdminAuthorizationService.requireAuthorizedRegionId(USER_ID)).thenReturn(REGION_ID);
        when(contentService.findContentsByRegionIdAndStatus(REGION_ID, ContentStatus.APPROVED)).thenReturn(List.of(
            laterContent,
            tiedContent,
            firstContent
        ));
        when(contentLogService.findLatestByContentIdsAndStatus(anyList(), eq(ContentLogStatus.APPROVED)))
            .thenReturn(approvedLogs);
        stubImageUrl(firstContent, 100L, IMAGE_EXPIRES_AT);
        stubImageUrl(tiedContent, 101L, IMAGE_EXPIRES_AT);
        stubImageUrl(laterContent, 102L, IMAGE_EXPIRES_AT);

        RegionAdminContentListResult result = useCase.get(USER_ID, "APPROVED");

        assertThat(result.status()).isEqualTo(ContentStatus.APPROVED);
        assertThat(result.contents()).extracting(RegionAdminContentListResult.Content::contentId)
            .containsExactly(100L, 101L, 102L);
        assertThat(result.contents()).extracting(RegionAdminContentListResult.Content::submittedAt)
            .containsOnlyNulls();
        assertThat(result.contents()).extracting(RegionAdminContentListResult.Content::approvedAt)
            .containsExactly(
                SUBMITTED_AT.plusSeconds(10),
                SUBMITTED_AT.plusSeconds(20),
                SUBMITTED_AT.plusSeconds(30)
            );
    }

    @Test
    void APPROVED_콘텐츠에_승인로그가_없으면_URL을_발급하지_않고_정합성오류로_처리한다() {
        Content content = approvedContent(100L, SUBMITTED_AT.plusSeconds(60));
        when(regionAdminAuthorizationService.requireAuthorizedRegionId(USER_ID)).thenReturn(REGION_ID);
        when(contentService.findContentsByRegionIdAndStatus(REGION_ID, ContentStatus.APPROVED))
            .thenReturn(List.of(content));
        when(contentLogService.findLatestByContentIdsAndStatus(anyList(), eq(ContentLogStatus.APPROVED)))
            .thenReturn(Map.of());

        assertThatThrownBy(() -> useCase.get(USER_ID, "APPROVED"))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR)
            );
        verifyNoInteractions(representativeImageViewUrlService);
    }

    @Test
    void 다른_지역_APPROVED_콘텐츠는_URL을_발급하지_않고_정합성오류로_처리한다() {
        Content content = approvedContent(100L, SUBMITTED_AT.plusSeconds(60));
        ContentLog approvedLog = statusLog(ContentLogStatus.APPROVED, SUBMITTED_AT);
        when(content.isScopedTo(REGION_ID)).thenReturn(false);
        when(regionAdminAuthorizationService.requireAuthorizedRegionId(USER_ID)).thenReturn(REGION_ID);
        when(contentService.findContentsByRegionIdAndStatus(REGION_ID, ContentStatus.APPROVED))
            .thenReturn(List.of(content));
        when(contentLogService.findLatestByContentIdsAndStatus(anyList(), eq(ContentLogStatus.APPROVED)))
            .thenReturn(Map.of(100L, approvedLog));

        assertThatThrownBy(() -> useCase.get(USER_ID, "APPROVED"))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR)
            );
        verifyNoInteractions(representativeImageViewUrlService);
    }

    @Test
    void 목록이_없으면_빈_목록을_반환하고_URL이나_상태로그를_조회하지_않는다() {
        when(regionAdminAuthorizationService.requireAuthorizedRegionId(USER_ID)).thenReturn(REGION_ID);
        when(contentService.findContentsByRegionIdAndStatus(REGION_ID, ContentStatus.APPROVED))
            .thenReturn(List.of());

        RegionAdminContentListResult result = useCase.get(USER_ID, "APPROVED");

        assertThat(result.contents()).isEmpty();
        verifyNoInteractions(
            contentLogService,
            originalContentReviewTargetService,
            representativeImageViewUrlService
        );
    }

    @Test
    void status가_없거나_지원하지_않으면_인가_후_입력오류로_거부한다() {
        when(regionAdminAuthorizationService.requireAuthorizedRegionId(USER_ID)).thenReturn(REGION_ID);

        for (String status : new String[] {null, "PUBLISHED", "pending"}) {
            assertThatThrownBy(() -> useCase.get(USER_ID, status))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT)
                );
        }
        verifyNoInteractions(
            contentService,
            contentLogService,
            originalContentReviewTargetService,
            representativeImageViewUrlService
        );
    }

    @Test
    void 목록의_대표이미지_정합성이_깨지면_어떤_URL도_발급하지_않는다() {
        OriginalContentReviewTarget validTarget = pendingTarget(101L, SUBMITTED_AT, true);
        OriginalContentReviewTarget invalidTarget = pendingTarget(102L, SUBMITTED_AT.plusSeconds(1), true);
        Content validContent = validTarget.content();
        Content invalidContent = invalidTarget.content();
        when(invalidContent.getRepresentativeImageObject().getLifecycleStatus())
            .thenReturn(ImageLifecycleStatus.DELETE_PENDING);
        when(regionAdminAuthorizationService.requireAuthorizedRegionId(USER_ID)).thenReturn(REGION_ID);
        when(contentService.findContentsByRegionIdAndStatus(REGION_ID, ContentStatus.PENDING)).thenReturn(List.of(
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

    private OriginalContentReviewTarget pendingTarget(
        Long contentId,
        Instant submittedAt,
        boolean originalReviewTarget
    ) {
        Content content = content(contentId, ContentStatus.PENDING, submittedAt);
        ContentLog pendingLog = statusLog(ContentLogStatus.PENDING, submittedAt);
        OriginalContentReviewTarget target = mock(OriginalContentReviewTarget.class);
        when(target.content()).thenReturn(content);
        when(target.pendingLog()).thenReturn(pendingLog);
        when(target.isOriginalReviewTarget()).thenReturn(originalReviewTarget);
        return target;
    }

    private Content approvedContent(Long contentId, Instant publishAt) {
        return content(contentId, ContentStatus.APPROVED, publishAt);
    }

    private Content content(Long contentId, ContentStatus status, Instant publishAt) {
        Content content = mock(Content.class);
        AppUser operator = mock(AppUser.class);
        ImageObject representativeImageObject = mock(ImageObject.class);
        when(content.getContentId()).thenReturn(contentId);
        when(content.getDeletedAt()).thenReturn(null);
        when(content.getStatus()).thenReturn(status);
        when(content.isScopedTo(REGION_ID)).thenReturn(true);
        when(content.getContentType()).thenReturn(ContentType.EVENT_EXPERIENCE);
        when(content.getTitle()).thenReturn("콘텐츠 " + contentId);
        when(content.getPublishAt()).thenReturn(publishAt);
        when(content.getOperator()).thenReturn(operator);
        when(content.getRepresentativeImageObject()).thenReturn(representativeImageObject);
        when(content.getRepresentativeImageAssignedAt()).thenReturn(SUBMITTED_AT);
        when(operator.getUserId()).thenReturn(contentId + 100L);
        when(operator.getName()).thenReturn("운영자 " + contentId);
        when(representativeImageObject.getLifecycleStatus()).thenReturn(ImageLifecycleStatus.ACTIVE);
        when(representativeImageObject.getLinkedAt()).thenReturn(SUBMITTED_AT);
        when(representativeImageObject.isScopedTo(REGION_ID)).thenReturn(true);
        return content;
    }

    private ContentLog statusLog(ContentLogStatus status, Instant statusAt) {
        ContentLog contentLog = mock(ContentLog.class);
        when(contentLog.getStatus()).thenReturn(status);
        when(contentLog.getDate()).thenReturn(statusAt);
        return contentLog;
    }

    private void stubImageUrl(Content content, Long contentId, Instant expiresAt) {
        when(representativeImageViewUrlService.createViewUrl(content.getRepresentativeImageObject()))
            .thenReturn(new RepresentativeImageViewUrl(
                "https://example.invalid/view/" + contentId,
                expiresAt
            ));
    }
}
