package io.regionevent.regioneventbackend.domain.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
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
import io.regionevent.regioneventbackend.domain.reservation.entity.Reservation;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationStatus;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignmentId;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class SearchOperatorReservationByNumberUseCaseTest {

    private static final Long OPERATOR_USER_ID = 1L;
    private static final Long RESERVATION_ID = 10L;
    private static final String RESERVATION_NO = "R20260804ABCDEFGHJKLM";
    private static final UUID REQUEST_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant DATABASE_NOW = Instant.parse("2026-08-04T01:00:00Z");

    @Test
    void search_체크인_시작_시각이면_canCheckIn을_true로_반환하고_감사를_기록한다() {
        TestContext context = new TestContext(DATABASE_NOW, DATABASE_NOW.plusSeconds(3_600));

        OperatorReservationSearchResult result = context.useCase.search(
            OPERATOR_USER_ID,
            RESERVATION_NO,
            REQUEST_ID
        );

        assertThat(result.canCheckIn()).isTrue();
        assertThat(result.participant().name()).isEqualTo("김*수");
        assertThat(result.participant().phone()).isEqualTo("010-****-5678");

        ArgumentCaptor<AuditEventCommand> commandCaptor = ArgumentCaptor.forClass(AuditEventCommand.class);
        verify(context.recordAuditEventUseCase).record(commandCaptor.capture());
        AuditEventCommand command = commandCaptor.getValue();
        assertThat(command.requestId()).isEqualTo(REQUEST_ID);
        assertThat(command.region()).isSameAs(context.region);
        assertThat(command.targetType()).isEqualTo(AuditEventTargetType.RESERVATION);
        assertThat(command.targetId()).isEqualTo(RESERVATION_ID);
        assertThat(command.previousState()).isEqualTo(ReservationStatus.CONFIRMED.name());
        assertThat(command.nextState()).isEqualTo(ReservationStatus.CONFIRMED.name());
        assertThat(command.result()).isEqualTo(AuditEventResult.SUCCESS);
        assertThat(command.reasonCode()).isEqualTo("QR_VERIFICATION_FAILED");
        assertThat(command.occurredAt()).isEqualTo(DATABASE_NOW);
    }

    @Test
    void search_체크인_종료_시각이면_canCheckIn을_false로_반환한다() {
        TestContext context = new TestContext(DATABASE_NOW.minusSeconds(3_600), DATABASE_NOW);

        OperatorReservationSearchResult result = context.useCase.search(
            OPERATOR_USER_ID,
            RESERVATION_NO,
            REQUEST_ID
        );

        assertThat(result.canCheckIn()).isFalse();
    }

    @Test
    void search_소유_운영자_인가에_실패하면_감사를_기록하지_않는다() {
        TestContext context = new TestContext(DATABASE_NOW, DATABASE_NOW.plusSeconds(3_600));
        when(context.operatorAuthorizationService.authorizeOwnedContent(any(), any(), any()))
            .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        assertThatThrownBy(() -> context.useCase.search(OPERATOR_USER_ID, RESERVATION_NO, REQUEST_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN)
            );

        verifyNoInteractions(context.recordAuditEventUseCase);
    }

    private static class TestContext {

        private final ReservationReadService reservationReadService = mock(ReservationReadService.class);
        private final ReservationService reservationService = mock(ReservationService.class);
        private final OperatorAuthorizationService operatorAuthorizationService = mock(
            OperatorAuthorizationService.class
        );
        private final ReservationParticipantMasker reservationParticipantMasker = mock(
            ReservationParticipantMasker.class
        );
        private final RecordAuditEventUseCase recordAuditEventUseCase = mock(RecordAuditEventUseCase.class);
        private final Region region = mock(Region.class);
        private final SearchOperatorReservationByNumberUseCase useCase =
            new SearchOperatorReservationByNumberUseCase(
                reservationReadService,
                reservationService,
                operatorAuthorizationService,
                reservationParticipantMasker,
                recordAuditEventUseCase
            );

        private TestContext(Instant checkinOpenAt, Instant checkinCloseAt) {
            ReservationReadSnapshot snapshot = new ReservationReadSnapshot(
                new ReservationReadSnapshot.ReservationInfo(
                    RESERVATION_ID,
                    RESERVATION_NO,
                    ReservationStatus.CONFIRMED,
                    Instant.parse("2026-08-03T01:00:00Z"),
                    null,
                    null,
                    null,
                    100L
                ),
                new ReservationReadSnapshot.SessionInfo(
                    20L,
                    ContentSessionStatus.SCHEDULED,
                    Instant.parse("2026-08-04T01:30:00Z"),
                    Instant.parse("2026-08-04T03:30:00Z"),
                    checkinOpenAt,
                    checkinCloseAt,
                    100L
                ),
                new ReservationReadSnapshot.ContentInfo(30L, "김해 가야문화 체험", 100L),
                new ReservationReadSnapshot.ParticipantInfo(40L, "김민수", "01012345678")
            );
            ReservationReadResult readResult = new ReservationReadResult(
                snapshot,
                new ReservationReadIntegrityValidator.CheckInInfo(null, false, null)
            );
            Reservation reservation = reservation(region);

            when(reservationReadService.findByReservationNo(RESERVATION_NO)).thenReturn(readResult);
            when(reservationService.findByReservationNoForAuthorizedLookup(RESERVATION_NO)).thenReturn(reservation);
            when(reservationService.findCurrentDatabaseInstant()).thenReturn(DATABASE_NOW);
            OperatorAuthorizationService.AuthorizedOperator authorizedOperator = authorizedOperator(region);
            when(operatorAuthorizationService.authorizeOwnedContent(any(), any(), any())).thenReturn(
                authorizedOperator
            );
            when(reservationParticipantMasker.mask(snapshot.participant())).thenReturn(
                new ReservationParticipantMasker.MaskedParticipant("김*수", "010-****-5678")
            );
        }

        private Reservation reservation(Region region) {
            Reservation reservation = mock(Reservation.class);
            ContentSession session = mock(ContentSession.class);
            Content content = mock(Content.class);
            AppUser contentOperator = mock(AppUser.class);

            when(reservation.getReservationId()).thenReturn(RESERVATION_ID);
            when(reservation.getStatus()).thenReturn(ReservationStatus.CONFIRMED);
            when(reservation.getRegion()).thenReturn(region);
            when(reservation.getContentSession()).thenReturn(session);
            when(session.getContent()).thenReturn(content);
            when(content.getOperator()).thenReturn(contentOperator);
            when(content.getRegion()).thenReturn(region);
            return reservation;
        }

        private OperatorAuthorizationService.AuthorizedOperator authorizedOperator(Region region) {
            AppUser operator = mock(AppUser.class);
            UserRoleAssignment roleAssignment = mock(UserRoleAssignment.class);

            when(operator.getUserId()).thenReturn(OPERATOR_USER_ID);
            when(operator.getStatus()).thenReturn(AppUserStatus.ACTIVE);
            when(roleAssignment.getId()).thenReturn(new UserRoleAssignmentId(OPERATOR_USER_ID, UserRole.OPERATOR));
            when(roleAssignment.getAppUser()).thenReturn(operator);
            when(roleAssignment.getRole()).thenReturn(UserRole.OPERATOR);
            when(region.getRegionId()).thenReturn(100L);
            return new OperatorAuthorizationService.AuthorizedOperator(operator, region, roleAssignment);
        }
    }
}
