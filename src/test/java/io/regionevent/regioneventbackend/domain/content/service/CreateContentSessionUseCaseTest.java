package io.regionevent.regioneventbackend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.audit.service.RecordFailedAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.content.dto.CreateContentSessionRequest;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignmentId;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService.AuthorizedOperator;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class CreateContentSessionUseCaseTest {

    private static final Long USER_ID = 100L;
    private static final Long CONTENT_ID = 200L;
    private static final Long REGION_ID = 10L;
    private static final Instant INITIAL_NOW = Instant.parse("2026-08-05T00:00:00Z");
    private static final Instant STARTS_AT = INITIAL_NOW.plusSeconds(60);

    private final OperatorAuthorizationService operatorAuthorizationService = mock(
        OperatorAuthorizationService.class
    );
    private final ContentService contentService = mock(ContentService.class);
    private final ContentSessionService contentSessionService = mock(ContentSessionService.class);
    private final RecordAuditEventUseCase recordAuditEventUseCase = mock(RecordAuditEventUseCase.class);
    private final RecordFailedAuditEventUseCase recordFailedAuditEventUseCase = mock(
        RecordFailedAuditEventUseCase.class
    );
    private final Clock clock = mock(Clock.class);
    private final CreateContentSessionUseCase useCase = new CreateContentSessionUseCase(
        operatorAuthorizationService,
        contentService,
        contentSessionService,
        recordAuditEventUseCase,
        recordFailedAuditEventUseCase,
        clock
    );

    @Test
    void create_콘텐츠_잠금_대기중_시작시각이_지나면_회차를_생성하지_않는다() {
        AuthorizedOperator operator = authorizedOperator();
        Content content = mock(Content.class);
        when(clock.instant()).thenReturn(INITIAL_NOW, STARTS_AT);
        when(operatorAuthorizationService.requireAuthorizedOperator(USER_ID)).thenReturn(operator);
        when(contentService.findOwnedContentForRevisionCreation(CONTENT_ID, USER_ID, REGION_ID))
            .thenReturn(content);
        when(content.getStatus()).thenReturn(ContentStatus.PUBLISHED);
        when(content.getPublishAt()).thenReturn(INITIAL_NOW.minusSeconds(60));

        assertThatThrownBy(() -> useCase.create(
            USER_ID,
            CONTENT_ID,
            request(),
            UUID.randomUUID()
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT)
        );

        verify(contentSessionService, never()).createPendingSessions(
            any(),
            any(),
            any()
        );
        verify(recordAuditEventUseCase, never()).record(any());
    }

    private AuthorizedOperator authorizedOperator() {
        AppUser user = mock(AppUser.class);
        Region region = mock(Region.class);
        UserRoleAssignment assignment = mock(UserRoleAssignment.class);
        UserRoleAssignmentId assignmentId = mock(UserRoleAssignmentId.class);
        when(user.getUserId()).thenReturn(USER_ID);
        when(region.getRegionId()).thenReturn(REGION_ID);
        when(assignment.getId()).thenReturn(assignmentId);
        return new AuthorizedOperator(user, region, assignment);
    }

    private CreateContentSessionRequest request() {
        return new CreateContentSessionRequest(
            OffsetDateTime.ofInstant(STARTS_AT, ZoneOffset.ofHours(9)),
            OffsetDateTime.ofInstant(STARTS_AT.plusSeconds(7_200), ZoneOffset.ofHours(9)),
            OffsetDateTime.ofInstant(STARTS_AT.minusSeconds(1_800), ZoneOffset.ofHours(9)),
            OffsetDateTime.ofInstant(STARTS_AT.plusSeconds(5_400), ZoneOffset.ofHours(9)),
            30
        );
    }
}
