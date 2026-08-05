package io.regionevent.regioneventbackend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class GetPendingSessionReviewDetailUseCaseTest {

    @Test
    void get_whenSessionIsReviewTargetInAuthorizedRegion_returnsDetail() {
        RegionAdminAuthorizationService authorizationService = mock(RegionAdminAuthorizationService.class);
        ContentSessionService contentSessionService = mock(ContentSessionService.class);
        GetPendingSessionReviewDetailUseCase useCase = new GetPendingSessionReviewDetailUseCase(authorizationService, contentSessionService);
        ContentSession session = session(1L);
        when(authorizationService.requireAuthorizedRegionId(10L)).thenReturn(1L);
        when(contentSessionService.findPendingReviewTarget(20L)).thenReturn(session);

        useCase.get(10L, 20L);
    }

    @Test
    void get_whenSessionIsNotReviewTarget_throwsNotFound() {
        RegionAdminAuthorizationService authorizationService = mock(RegionAdminAuthorizationService.class);
        ContentSessionService contentSessionService = mock(ContentSessionService.class);
        GetPendingSessionReviewDetailUseCase useCase = new GetPendingSessionReviewDetailUseCase(authorizationService, contentSessionService);
        when(authorizationService.requireAuthorizedRegionId(10L)).thenReturn(1L);
        when(contentSessionService.findPendingReviewTarget(20L)).thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

        assertThatThrownBy(() -> useCase.get(10L, 20L))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                org.assertj.core.api.Assertions.assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void get_whenRegionDiffers_throwsForbidden() {
        RegionAdminAuthorizationService authorizationService = mock(RegionAdminAuthorizationService.class);
        ContentSessionService contentSessionService = mock(ContentSessionService.class);
        GetPendingSessionReviewDetailUseCase useCase = new GetPendingSessionReviewDetailUseCase(authorizationService, contentSessionService);
        ContentSession session = session(1L);
        when(authorizationService.requireAuthorizedRegionId(10L)).thenReturn(2L);
        when(contentSessionService.findPendingReviewTarget(20L)).thenReturn(session);

        assertThatThrownBy(() -> useCase.get(10L, 20L))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                org.assertj.core.api.Assertions.assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    private ContentSession session(Long regionId) {
        ContentSession session = mock(ContentSession.class);
        Content content = mock(Content.class);
        Region region = mock(Region.class);
        AppUser operator = mock(AppUser.class);
        when(region.getRegionId()).thenReturn(regionId);
        when(operator.getUserId()).thenReturn(30L);
        when(operator.getName()).thenReturn("운영자");
        when(content.getContentId()).thenReturn(40L);
        when(content.getTitle()).thenReturn("콘텐츠");
        when(content.getStatus()).thenReturn(ContentStatus.APPROVED);
        when(content.getOperator()).thenReturn(operator);
        when(session.getSessionId()).thenReturn(20L);
        when(session.getRegion()).thenReturn(region);
        when(session.getContent()).thenReturn(content);
        when(session.getStatus()).thenReturn(ContentSessionStatus.PENDING);
        when(session.getStartsAt()).thenReturn(Instant.now());
        when(session.getEndsAt()).thenReturn(Instant.now().plusSeconds(3600));
        when(session.getCheckinOpenAt()).thenReturn(Instant.now());
        when(session.getCheckinCloseAt()).thenReturn(Instant.now().plusSeconds(3600));
        when(session.getCreatedAt()).thenReturn(Instant.now());
        return session;
    }
}
