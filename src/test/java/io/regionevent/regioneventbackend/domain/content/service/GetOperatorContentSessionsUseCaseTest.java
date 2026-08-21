package io.regionevent.regioneventbackend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.SessionRevision;
import io.regionevent.regioneventbackend.domain.content.entity.SessionRevisionStatus;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class GetOperatorContentSessionsUseCaseTest {

    private static final long USER_ID = 10L;
    private static final long CONTENT_ID = 20L;
    private static final long REGION_ID = 30L;
    private static final Instant STARTS_AT = Instant.parse("2026-08-22T01:00:00Z");

    private final ContentService contentService = mock(ContentService.class);
    private final ContentSessionService contentSessionService = mock(ContentSessionService.class);
    private final SessionRevisionService sessionRevisionService = mock(SessionRevisionService.class);
    private final OperatorAuthorizationService operatorAuthorizationService = mock(OperatorAuthorizationService.class);
    private final GetOperatorContentSessionsUseCase useCase = new GetOperatorContentSessionsUseCase(
        contentService,
        contentSessionService,
        sessionRevisionService,
        operatorAuthorizationService
    );

    @Test
    void get_소유_콘텐츠의_회차와_현재_변경_요청을_조회_순서대로_반환한다() {
        Content content = content();
        AppUser operator = content.getOperator();
        Region region = content.getRegion();
        ContentSession firstSession = session(content, 100L, ContentSessionStatus.PENDING);
        ContentSession secondSession = session(content, 101L, ContentSessionStatus.SCHEDULED);
        SessionRevision pendingRevision = pendingRevision(content, secondSession, 200L);
        when(contentService.findOperatorReservationListTarget(CONTENT_ID)).thenReturn(content);
        when(contentSessionService.findCurrentSessionsByContentId(CONTENT_ID))
            .thenReturn(List.of(firstSession, secondSession));
        when(sessionRevisionService.findPendingByTargetContentId(CONTENT_ID))
            .thenReturn(List.of(pendingRevision));

        OperatorContentSessionListResult result = useCase.get(USER_ID, CONTENT_ID);

        assertThat(result.contentId()).isEqualTo(CONTENT_ID);
        assertThat(result.sessions()).extracting(OperatorContentSessionListResult.Session::sessionId)
            .containsExactly(100L, 101L);
        assertThat(result.sessions().getFirst().pendingChangeRequest()).isNull();
        assertThat(result.sessions().get(1).pendingChangeRequest()).satisfies(changeRequest -> {
            assertThat(changeRequest.revisionId()).isEqualTo(200L);
            assertThat(changeRequest.status()).isEqualTo(SessionRevisionStatus.PENDING);
            assertThat(changeRequest.baseSessionVersion()).isEqualTo(3);
            assertThat(changeRequest.candidate().capacity()).isEqualTo(40);
        });
        InOrder inOrder = inOrder(
            contentService,
            operatorAuthorizationService,
            contentSessionService,
            sessionRevisionService
        );
        inOrder.verify(contentService).findOperatorReservationListTarget(CONTENT_ID);
        inOrder.verify(operatorAuthorizationService).authorizeOwnedContent(
            USER_ID,
            operator,
            region
        );
        inOrder.verify(contentSessionService).findCurrentSessionsByContentId(CONTENT_ID);
        inOrder.verify(sessionRevisionService).findPendingByTargetContentId(CONTENT_ID);
    }

    @Test
    void get_회차의_콘텐츠나_지역_관계가_다르면_내부_오류를_반환한다() {
        Content content = content();
        ContentSession contentSession = session(content, 100L, ContentSessionStatus.PENDING);
        Region otherRegion = mock(Region.class);
        when(otherRegion.getRegionId()).thenReturn(999L);
        when(contentSession.getRegion()).thenReturn(otherRegion);
        when(contentService.findOperatorReservationListTarget(CONTENT_ID)).thenReturn(content);
        when(contentSessionService.findCurrentSessionsByContentId(CONTENT_ID)).thenReturn(List.of(contentSession));
        when(sessionRevisionService.findPendingByTargetContentId(CONTENT_ID)).thenReturn(List.of());

        assertInternalServerError(() -> useCase.get(USER_ID, CONTENT_ID));
    }

    @Test
    void get_변경_요청의_대상_관계가_다르면_내부_오류를_반환한다() {
        Content content = content();
        ContentSession contentSession = session(content, 100L, ContentSessionStatus.SCHEDULED);
        SessionRevision pendingRevision = pendingRevision(content, contentSession, 200L);
        Content otherContent = mock(Content.class);
        when(otherContent.getContentId()).thenReturn(999L);
        when(pendingRevision.getContent()).thenReturn(otherContent);
        when(contentService.findOperatorReservationListTarget(CONTENT_ID)).thenReturn(content);
        when(contentSessionService.findCurrentSessionsByContentId(CONTENT_ID)).thenReturn(List.of(contentSession));
        when(sessionRevisionService.findPendingByTargetContentId(CONTENT_ID))
            .thenReturn(List.of(pendingRevision));

        assertInternalServerError(() -> useCase.get(USER_ID, CONTENT_ID));
    }

    @Test
    void get_운영자_인가에_실패하면_회차와_변경_요청을_조회하지_않는다() {
        Content content = content();
        AppUser operator = content.getOperator();
        Region region = content.getRegion();
        when(contentService.findOperatorReservationListTarget(CONTENT_ID)).thenReturn(content);
        doThrow(new BusinessException(ErrorCode.FORBIDDEN))
            .when(operatorAuthorizationService)
            .authorizeOwnedContent(USER_ID, operator, region);

        assertThatThrownBy(() -> useCase.get(USER_ID, CONTENT_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN)
            );
        verifyNoInteractions(contentSessionService, sessionRevisionService);
    }

    private Content content() {
        Content content = mock(Content.class);
        AppUser operator = mock(AppUser.class);
        Region region = mock(Region.class);
        when(content.getContentId()).thenReturn(CONTENT_ID);
        when(content.getOperator()).thenReturn(operator);
        when(content.getRegion()).thenReturn(region);
        when(region.getRegionId()).thenReturn(REGION_ID);
        return content;
    }

    private ContentSession session(
        Content content,
        Long sessionId,
        ContentSessionStatus status
    ) {
        ContentSession contentSession = mock(ContentSession.class);
        Region region = content.getRegion();
        when(contentSession.getSessionId()).thenReturn(sessionId);
        when(contentSession.getContent()).thenReturn(content);
        when(contentSession.getRegion()).thenReturn(region);
        when(contentSession.getStatus()).thenReturn(status);
        when(contentSession.getVersionNo()).thenReturn(3);
        when(contentSession.getStartsAt()).thenReturn(STARTS_AT);
        when(contentSession.getEndsAt()).thenReturn(STARTS_AT.plusSeconds(7_200));
        when(contentSession.getCheckinOpenAt()).thenReturn(STARTS_AT.minusSeconds(1_800));
        when(contentSession.getCheckinCloseAt()).thenReturn(STARTS_AT.plusSeconds(5_400));
        when(contentSession.getCapacity()).thenReturn(30);
        when(contentSession.getRemainingCapacity()).thenReturn(20);
        when(contentSession.getCreatedAt()).thenReturn(Instant.parse("2026-08-01T00:00:00Z"));
        return contentSession;
    }

    private SessionRevision pendingRevision(
        Content content,
        ContentSession targetSession,
        Long revisionId
    ) {
        SessionRevision revision = mock(SessionRevision.class);
        Region region = content.getRegion();
        when(revision.getSessionRevisionId()).thenReturn(revisionId);
        when(revision.getStatus()).thenReturn(SessionRevisionStatus.PENDING);
        when(revision.getContent()).thenReturn(content);
        when(revision.getRegion()).thenReturn(region);
        when(revision.getTargetSession()).thenReturn(targetSession);
        when(revision.getBaseSessionVersion()).thenReturn(3);
        when(revision.getStartsAt()).thenReturn(STARTS_AT.plusSeconds(86_400));
        when(revision.getEndsAt()).thenReturn(STARTS_AT.plusSeconds(93_600));
        when(revision.getCheckinOpenAt()).thenReturn(STARTS_AT.plusSeconds(84_600));
        when(revision.getCheckinCloseAt()).thenReturn(STARTS_AT.plusSeconds(91_800));
        when(revision.getCapacity()).thenReturn(40);
        when(revision.getSubmittedAt()).thenReturn(Instant.parse("2026-08-02T00:00:00Z"));
        return revision;
    }

    private void assertInternalServerError(Runnable action) {
        assertThatThrownBy(action::run)
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR)
            );
    }
}
