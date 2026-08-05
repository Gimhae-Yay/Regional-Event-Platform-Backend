package io.regionevent.regioneventbackend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.content.entity.SessionRevision;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class GetSessionRevisionReviewDetailUseCaseTest {

    private static final Long USER_ID = 1L;
    private static final Long REGION_ID = 10L;
    private static final Long REVISION_ID = 52L;

    private final RegionAdminAuthorizationService regionAdminAuthorizationService =
        mock(RegionAdminAuthorizationService.class);
    private final SessionRevisionService sessionRevisionService = mock(SessionRevisionService.class);
    private final GetSessionRevisionReviewDetailUseCase useCase = new GetSessionRevisionReviewDetailUseCase(
        regionAdminAuthorizationService,
        sessionRevisionService
    );

    @Test
    void 담당_지역의_심사_대기_수정_요청을_현재_회차와_후보로_조립한다() {
        SessionRevision revision = revision(REGION_ID, REGION_ID, REGION_ID);
        when(regionAdminAuthorizationService.requireAuthorizedRegionId(USER_ID)).thenReturn(REGION_ID);
        when(sessionRevisionService.findPendingReviewDetailById(REVISION_ID)).thenReturn(revision);

        SessionRevisionReviewDetailResult result = useCase.get(USER_ID, REVISION_ID);

        assertThat(result.revisionId()).isEqualTo(REVISION_ID);
        assertThat(result.contentId()).isEqualTo(10L);
        assertThat(result.contentTitle()).isEqualTo("가야문화 체험");
        assertThat(result.contentStatus()).isEqualTo(ContentStatus.PUBLISHED);
        assertThat(result.targetSession().status()).isEqualTo(ContentSessionStatus.SCHEDULED);
        assertThat(result.targetSession().version()).isEqualTo(3);
        assertThat(result.candidate().startsAt()).isEqualTo(Instant.parse("2026-08-29T01:00:00Z"));
        assertThat(result.baseSessionVersion()).isEqualTo(3);
        assertThat(result.operator().operatorId()).isEqualTo(20L);
    }

    @Test
    void 담당_지역이_다르면_상세를_반환하지_않는다() {
        SessionRevision revision = revision(20L, 20L, 20L);
        when(regionAdminAuthorizationService.requireAuthorizedRegionId(USER_ID)).thenReturn(REGION_ID);
        when(sessionRevisionService.findPendingReviewDetailById(REVISION_ID)).thenReturn(revision);

        assertThatThrownBy(() -> useCase.get(USER_ID, REVISION_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN)
            );
    }

    @Test
    void 수정_요청과_대상_회차의_관계가_일치하지_않으면_서버_오류로_처리한다() {
        SessionRevision revision = revision(REGION_ID, REGION_ID, 20L);
        when(regionAdminAuthorizationService.requireAuthorizedRegionId(USER_ID)).thenReturn(REGION_ID);
        when(sessionRevisionService.findPendingReviewDetailById(REVISION_ID)).thenReturn(revision);

        assertThatThrownBy(() -> useCase.get(USER_ID, REVISION_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR)
            );
    }

    @Test
    void 지역_관리자_권한이_없으면_수정_요청을_조회하지_않는다() {
        when(regionAdminAuthorizationService.requireAuthorizedRegionId(USER_ID))
            .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        assertThatThrownBy(() -> useCase.get(USER_ID, REVISION_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN)
            );

        verifyNoInteractions(sessionRevisionService);
    }

    private SessionRevision revision(
        Long revisionRegionId,
        Long contentRegionId,
        Long targetSessionRegionId
    ) {
        SessionRevision revision = mock(SessionRevision.class);
        Content content = mock(Content.class);
        ContentSession targetSession = mock(ContentSession.class);
        AppUser requestedBy = mock(AppUser.class);
        Region revisionRegion = region(revisionRegionId);
        Region contentRegion = region(contentRegionId);
        Region targetSessionRegion = region(targetSessionRegionId);
        Content targetContent = mock(Content.class);
        when(revision.getSessionRevisionId()).thenReturn(REVISION_ID);
        when(revision.getRegion()).thenReturn(revisionRegion);
        when(revision.getContent()).thenReturn(content);
        when(revision.getTargetSession()).thenReturn(targetSession);
        when(revision.getRequestedBy()).thenReturn(requestedBy);
        when(revision.getBaseSessionVersion()).thenReturn(3);
        when(revision.getStartsAt()).thenReturn(Instant.parse("2026-08-29T01:00:00Z"));
        when(revision.getEndsAt()).thenReturn(Instant.parse("2026-08-29T03:00:00Z"));
        when(revision.getCheckinOpenAt()).thenReturn(Instant.parse("2026-08-29T00:30:00Z"));
        when(revision.getCheckinCloseAt()).thenReturn(Instant.parse("2026-08-29T02:30:00Z"));
        when(revision.getCapacity()).thenReturn(30);
        when(revision.getSubmittedAt()).thenReturn(Instant.parse("2026-08-01T01:00:00Z"));
        when(content.getContentId()).thenReturn(10L);
        when(content.getTitle()).thenReturn("가야문화 체험");
        when(content.getStatus()).thenReturn(ContentStatus.PUBLISHED);
        when(content.getRegion()).thenReturn(contentRegion);
        when(targetSession.getSessionId()).thenReturn(21L);
        when(targetSession.getContent()).thenReturn(targetContent);
        when(targetSession.getRegion()).thenReturn(targetSessionRegion);
        when(targetSession.getStatus()).thenReturn(ContentSessionStatus.SCHEDULED);
        when(targetSession.getVersionNo()).thenReturn(3);
        when(targetSession.getStartsAt()).thenReturn(Instant.parse("2026-08-22T01:00:00Z"));
        when(targetSession.getEndsAt()).thenReturn(Instant.parse("2026-08-22T03:00:00Z"));
        when(targetSession.getCheckinOpenAt()).thenReturn(Instant.parse("2026-08-22T00:30:00Z"));
        when(targetSession.getCheckinCloseAt()).thenReturn(Instant.parse("2026-08-22T02:30:00Z"));
        when(targetSession.getCapacity()).thenReturn(30);
        when(targetSession.getRemainingCapacity()).thenReturn(20);
        when(targetContent.getContentId()).thenReturn(10L);
        when(requestedBy.getUserId()).thenReturn(20L);
        when(requestedBy.getName()).thenReturn("김해운영");
        return revision;
    }

    private Region region(Long regionId) {
        Region region = mock(Region.class);
        when(region.getRegionId()).thenReturn(regionId);
        return region;
    }
}
