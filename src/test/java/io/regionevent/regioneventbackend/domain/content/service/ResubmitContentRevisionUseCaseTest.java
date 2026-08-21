package io.regionevent.regioneventbackend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevision;
import io.regionevent.regioneventbackend.domain.content.entity.ContentRevisionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService.AuthorizedOperator;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class ResubmitContentRevisionUseCaseTest {

    private static final Long USER_ID = 100L;
    private static final Long REGION_ID = 10L;
    private static final Long CONTENT_ID = 200L;
    private static final Long SOURCE_REVISION_ID = 501L;
    private static final Long REVISION_ID = 502L;
    private static final Instant RESUBMITTED_AT = Instant.parse("2026-08-21T01:00:00Z");
    private static final Instant PUBLISH_AT = Instant.parse("2026-08-25T01:00:00Z");

    private final OperatorAuthorizationService operatorAuthorizationService =
        mock(OperatorAuthorizationService.class);
    private final ContentService contentService = mock(ContentService.class);
    private final ContentRevisionService contentRevisionService = mock(ContentRevisionService.class);
    private final ContentLogService contentLogService = mock(ContentLogService.class);
    private final ResubmitContentRevisionUseCase useCase = new ResubmitContentRevisionUseCase(
        operatorAuthorizationService,
        contentService,
        contentRevisionService,
        contentLogService,
        Clock.fixed(RESUBMITTED_AT, ZoneOffset.UTC)
    );

    @Test
    void resubmit_공개_콘텐츠의_최신_반려_수정본을_잠금_순서대로_재제출한다() {
        Fixture fixture = fixture(ContentStatus.PUBLISHED, null);
        stubTarget(fixture);

        ResubmitContentRevisionResult result = useCase.resubmit(USER_ID, SOURCE_REVISION_ID);

        assertThat(result).isEqualTo(new ResubmitContentRevisionResult(
            REVISION_ID,
            SOURCE_REVISION_ID,
            CONTENT_ID,
            ContentRevisionStatus.EDIT_REQUESTED,
            3,
            RESUBMITTED_AT
        ));
        InOrder lockOrder = inOrder(contentService, contentRevisionService);
        lockOrder.verify(contentRevisionService).findContentIdByRevisionId(SOURCE_REVISION_ID);
        lockOrder.verify(contentService).findRevisionResubmissionTargetForUpdate(CONTENT_ID);
        lockOrder.verify(contentRevisionService).findResubmissionSourceForUpdate(SOURCE_REVISION_ID);
        verify(contentRevisionService).validateLatestRejectedRevision(fixture.sourceRevision());
        verifyNoInteractions(contentLogService);
    }

    @Test
    void resubmit_공개전_재보완_대기_콘텐츠는_이력을_확인하고_재제출한다() {
        Fixture fixture = fixture(ContentStatus.PENDING, PUBLISH_AT);
        stubTarget(fixture);
        when(contentLogService.hasApprovedToPendingRevisionHistory(fixture.content())).thenReturn(true);

        useCase.resubmit(USER_ID, SOURCE_REVISION_ID);

        verify(contentLogService).hasApprovedToPendingRevisionHistory(fixture.content());
        verify(contentRevisionService).resubmitRejectedRevision(
            fixture.content(),
            fixture.sourceRevision(),
            fixture.operator().user(),
            RESUBMITTED_AT
        );
    }

    @Test
    void resubmit_소유자나_지역이_다르면_권한_오류를_반환한다() {
        Fixture fixture = fixture(ContentStatus.PUBLISHED, null);
        stubTarget(fixture);
        when(fixture.content().isOwnedBy(USER_ID)).thenReturn(false);

        assertThatThrownBy(() -> useCase.resubmit(USER_ID, SOURCE_REVISION_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN)
            );
        verify(contentRevisionService, never()).validateLatestRejectedRevision(fixture.sourceRevision());
    }

    @Test
    void resubmit_일반_대기_콘텐츠는_상태_충돌을_반환한다() {
        Fixture fixture = fixture(ContentStatus.PENDING, PUBLISH_AT);
        stubTarget(fixture);
        when(contentLogService.hasApprovedToPendingRevisionHistory(fixture.content())).thenReturn(false);

        assertContentStateConflict(() -> useCase.resubmit(USER_ID, SOURCE_REVISION_ID));

        verify(contentRevisionService, never()).resubmitRejectedRevision(
            fixture.content(),
            fixture.sourceRevision(),
            fixture.operator().user(),
            RESUBMITTED_AT
        );
    }

    @Test
    void resubmit_원본_상태와_후보_공개시각이_맞지_않으면_상태_충돌을_반환한다() {
        Fixture fixture = fixture(ContentStatus.PUBLISHED, PUBLISH_AT);
        stubTarget(fixture);

        assertContentStateConflict(() -> useCase.resubmit(USER_ID, SOURCE_REVISION_ID));
    }

    private void stubTarget(Fixture fixture) {
        when(operatorAuthorizationService.requireAuthorizedOperator(USER_ID)).thenReturn(fixture.operator());
        when(contentRevisionService.findContentIdByRevisionId(SOURCE_REVISION_ID)).thenReturn(CONTENT_ID);
        when(contentService.findRevisionResubmissionTargetForUpdate(CONTENT_ID)).thenReturn(fixture.content());
        when(contentRevisionService.findResubmissionSourceForUpdate(SOURCE_REVISION_ID))
            .thenReturn(fixture.sourceRevision());
        when(contentRevisionService.resubmitRejectedRevision(
            fixture.content(),
            fixture.sourceRevision(),
            fixture.operator().user(),
            RESUBMITTED_AT
        )).thenReturn(fixture.resubmittedRevision());
    }

    private static Fixture fixture(ContentStatus contentStatus, Instant candidatePublishAt) {
        AppUser user = mock(AppUser.class);
        Region region = mock(Region.class);
        UserRoleAssignment assignment = mock(UserRoleAssignment.class);
        Content content = mock(Content.class);
        ContentRevision sourceRevision = mock(ContentRevision.class);
        ContentRevision resubmittedRevision = mock(ContentRevision.class);
        when(user.getUserId()).thenReturn(USER_ID);
        when(region.getRegionId()).thenReturn(REGION_ID);
        when(assignment.getRoleAssignmentId()).thenReturn(1L);
        AuthorizedOperator operator = new AuthorizedOperator(user, region, assignment);
        when(content.getContentId()).thenReturn(CONTENT_ID);
        when(content.getStatus()).thenReturn(contentStatus);
        when(content.isOwnedBy(USER_ID)).thenReturn(true);
        when(content.isScopedTo(REGION_ID)).thenReturn(true);
        when(sourceRevision.getContentRevisionId()).thenReturn(SOURCE_REVISION_ID);
        when(sourceRevision.getPublishAt()).thenReturn(candidatePublishAt);
        when(resubmittedRevision.getContentRevisionId()).thenReturn(REVISION_ID);
        when(resubmittedRevision.getContent()).thenReturn(content);
        when(resubmittedRevision.getStatus()).thenReturn(ContentRevisionStatus.EDIT_REQUESTED);
        when(resubmittedRevision.getBaseContentVersion()).thenReturn(3);
        when(resubmittedRevision.getSubmittedAt()).thenReturn(RESUBMITTED_AT);
        return new Fixture(content, sourceRevision, resubmittedRevision, operator);
    }

    private static void assertContentStateConflict(Runnable action) {
        assertThatThrownBy(action::run)
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CONTENT_STATE_CONFLICT)
            );
    }

    private record Fixture(
        Content content,
        ContentRevision sourceRevision,
        ContentRevision resubmittedRevision,
        AuthorizedOperator operator
    ) {
    }
}
