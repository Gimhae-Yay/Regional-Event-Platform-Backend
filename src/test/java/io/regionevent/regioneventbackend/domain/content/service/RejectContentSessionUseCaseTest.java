package io.regionevent.regioneventbackend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.content.entity.Content;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignmentId;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class RejectContentSessionUseCaseTest {

    private static final Long USER_ID = 1L;
    private static final Long REGION_ID = 10L;
    private static final Long SESSION_ID = 100L;
    private static final Long CONTENT_ID = 200L;
    private static final UUID REQUEST_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant CLOCK_INSTANT = Instant.parse("2026-08-02T10:56:14.722444122Z");
    private static final Instant EXPECTED_REVIEWED_AT = Instant.parse("2026-08-02T10:56:14.722444Z");

    private final ContentSessionService contentSessionService = mock(ContentSessionService.class);
    private final RegionAdminAuthorizationService regionAdminAuthorizationService =
        mock(RegionAdminAuthorizationService.class);
    private final RecordAuditEventUseCase recordAuditEventUseCase = mock(RecordAuditEventUseCase.class);
    private final RejectContentSessionUseCase useCase = new RejectContentSessionUseCase(
        contentSessionService,
        regionAdminAuthorizationService,
        recordAuditEventUseCase,
        Clock.fixed(CLOCK_INSTANT, ZoneOffset.UTC)
    );

    @Test
    void 반려_시각과_감사_이벤트를_같은_정규화_시각으로_기록한다() {
        ContentSession pendingSession = mock(ContentSession.class);
        ContentSession rejectedSession = mock(ContentSession.class);
        Content content = mock(Content.class);
        Region region = mock(Region.class);
        UserRoleAssignment reviewer = mock(UserRoleAssignment.class);
        AppUser reviewerUser = mock(AppUser.class);

        when(contentSessionService.findRejectTargetForUpdate(SESSION_ID)).thenReturn(pendingSession);
        when(pendingSession.getRegion()).thenReturn(region);
        when(region.getRegionId()).thenReturn(REGION_ID);
        when(regionAdminAuthorizationService.authorize(USER_ID, REGION_ID)).thenReturn(reviewer);
        when(reviewer.getId()).thenReturn(new UserRoleAssignmentId(USER_ID, UserRole.REGION_ADMIN));
        when(reviewer.getAppUser()).thenReturn(reviewerUser);
        when(reviewerUser.getStatus()).thenReturn(AppUserStatus.ACTIVE);
        when(contentSessionService.reject(pendingSession, reviewerUser, EXPECTED_REVIEWED_AT, "보완 필요"))
            .thenReturn(rejectedSession);
        when(rejectedSession.getRegion()).thenReturn(region);
        when(rejectedSession.getSessionId()).thenReturn(SESSION_ID);
        when(rejectedSession.getContent()).thenReturn(content);
        when(content.getContentId()).thenReturn(CONTENT_ID);
        when(rejectedSession.getStatus()).thenReturn(ContentSessionStatus.REJECTED);
        when(rejectedSession.getRejectReason()).thenReturn("보완 필요");
        when(rejectedSession.getReviewedAt()).thenReturn(EXPECTED_REVIEWED_AT);

        RejectContentSessionResult result = useCase.reject(
            USER_ID,
            SESSION_ID,
            "  보완 필요  ",
            REQUEST_ID
        );

        assertThat(result).isEqualTo(new RejectContentSessionResult(
            SESSION_ID,
            CONTENT_ID,
            ContentSessionStatus.REJECTED,
            "보완 필요",
            EXPECTED_REVIEWED_AT
        ));
        ArgumentCaptor<AuditEventCommand> commandCaptor = ArgumentCaptor.forClass(AuditEventCommand.class);
        verify(recordAuditEventUseCase).record(commandCaptor.capture());
        assertThat(commandCaptor.getValue()).satisfies(command -> {
            assertThat(command.targetType()).isEqualTo(AuditEventTargetType.CONTENT_SESSION);
            assertThat(command.previousState()).isEqualTo("PENDING");
            assertThat(command.nextState()).isEqualTo("REJECTED");
            assertThat(command.result()).isEqualTo(AuditEventResult.SUCCESS);
            assertThat(command.occurredAt()).isEqualTo(EXPECTED_REVIEWED_AT);
        });
    }

    @Test
    void 반려_사유가_공백이면_입력_오류를_반환한다() {
        ContentSession pendingSession = mock(ContentSession.class);
        Region region = mock(Region.class);
        UserRoleAssignment reviewer = mock(UserRoleAssignment.class);

        when(contentSessionService.findRejectTargetForUpdate(SESSION_ID)).thenReturn(pendingSession);
        when(pendingSession.getRegion()).thenReturn(region);
        when(region.getRegionId()).thenReturn(REGION_ID);
        when(regionAdminAuthorizationService.authorize(USER_ID, REGION_ID)).thenReturn(reviewer);

        assertThatThrownBy(() -> useCase.reject(USER_ID, SESSION_ID, " ", REQUEST_ID))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(ErrorCode.INVALID_INPUT);
    }
}
