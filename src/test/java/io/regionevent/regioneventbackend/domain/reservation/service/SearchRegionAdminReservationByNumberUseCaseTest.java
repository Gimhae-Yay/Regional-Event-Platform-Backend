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
import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.reservation.entity.Reservation;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationStatus;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.domain.user.entity.AppUserStatus;
import io.regionevent.regioneventbackend.domain.user.entity.UserRole;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

class SearchRegionAdminReservationByNumberUseCaseTest {

    private static final Long REGION_ADMIN_USER_ID = 1L;
    private static final Long REGION_ID = 100L;
    private static final Long RESERVATION_ID = 10L;
    private static final String RESERVATION_NO = "R20260804ABCDEFGHJKLM";
    private static final UUID REQUEST_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant DATABASE_NOW = Instant.parse("2026-08-04T01:00:00Z");

    @Test
    void search_지역_관리자가_정상_조회하면_마스킹된_결과와_감사를_기록한다() {
        TestContext context = new TestContext();

        RegionAdminReservationSearchResult result = context.useCase.search(
            REGION_ADMIN_USER_ID,
            RESERVATION_NO,
            REQUEST_ID
        );

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
        assertThat(command.actor().getRole()).isEqualTo(UserRole.REGION_ADMIN);
        assertThat(command.occurredAt()).isEqualTo(DATABASE_NOW);
        verify(context.regionAdminAuthorizationService).authorize(REGION_ADMIN_USER_ID, REGION_ID);
    }

    @Test
    void search_지역_관리자_인가에_실패하면_감사를_기록하지_않는다() {
        TestContext context = new TestContext();
        when(context.regionAdminAuthorizationService.authorize(any(), any()))
            .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        assertThatThrownBy(() -> context.useCase.search(
            REGION_ADMIN_USER_ID,
            RESERVATION_NO,
            REQUEST_ID
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN)
        );

        verifyNoInteractions(context.recordAuditEventUseCase);
    }

    private static class TestContext {

        private final ReservationReadService reservationReadService = mock(ReservationReadService.class);
        private final ReservationService reservationService = mock(ReservationService.class);
        private final RegionAdminAuthorizationService regionAdminAuthorizationService = mock(
            RegionAdminAuthorizationService.class
        );
        private final ReservationParticipantMasker reservationParticipantMasker = mock(
            ReservationParticipantMasker.class
        );
        private final RecordAuditEventUseCase recordAuditEventUseCase = mock(RecordAuditEventUseCase.class);
        private final Region region = mock(Region.class);
        private final SearchRegionAdminReservationByNumberUseCase useCase =
            new SearchRegionAdminReservationByNumberUseCase(
                reservationReadService,
                reservationService,
                regionAdminAuthorizationService,
                reservationParticipantMasker,
                recordAuditEventUseCase
            );

        private TestContext() {
            ReservationReadSnapshot snapshot = new ReservationReadSnapshot(
                new ReservationReadSnapshot.ReservationInfo(
                    RESERVATION_ID,
                    RESERVATION_NO,
                    ReservationStatus.CONFIRMED,
                    Instant.parse("2026-08-03T01:00:00Z"),
                    null,
                    null,
                    null,
                    1,
                    REGION_ID
                ),
                new ReservationReadSnapshot.SessionInfo(
                    20L,
                    ContentSessionStatus.SCHEDULED,
                    Instant.parse("2026-08-04T01:30:00Z"),
                    Instant.parse("2026-08-04T03:30:00Z"),
                    Instant.parse("2026-08-04T00:30:00Z"),
                    Instant.parse("2026-08-04T01:30:00Z"),
                    REGION_ID
                ),
                new ReservationReadSnapshot.ContentInfo(30L, "김해 가야문화 체험", "김해시", REGION_ID),
                new ReservationReadSnapshot.ParticipantInfo(40L, "김민수", "01012345678")
            );
            ReservationReadResult readResult = new ReservationReadResult(
                snapshot,
                new ReservationReadIntegrityValidator.CheckInInfo(null, false, null)
            );

            Reservation reservation = reservation(region);
            UserRoleAssignment roleAssignment = roleAssignment();

            when(region.getRegionId()).thenReturn(REGION_ID);
            when(reservationReadService.findByReservationNo(RESERVATION_NO)).thenReturn(readResult);
            when(reservationService.findByReservationNoForAuthorizedLookup(RESERVATION_NO)).thenReturn(reservation);
            when(reservationService.findCurrentDatabaseInstant()).thenReturn(DATABASE_NOW);
            when(regionAdminAuthorizationService.authorize(REGION_ADMIN_USER_ID, REGION_ID)).thenReturn(roleAssignment);
            when(reservationParticipantMasker.mask(snapshot.participant())).thenReturn(
                new ReservationParticipantMasker.MaskedParticipant("김*수", "010-****-5678")
            );
        }

        private Reservation reservation(Region region) {
            Reservation reservation = mock(Reservation.class);
            ContentSession session = mock(ContentSession.class);

            when(reservation.getReservationId()).thenReturn(RESERVATION_ID);
            when(reservation.getStatus()).thenReturn(ReservationStatus.CONFIRMED);
            when(reservation.getRegion()).thenReturn(region);
            when(reservation.getContentSession()).thenReturn(session);
            return reservation;
        }

        private UserRoleAssignment roleAssignment() {
            AppUser regionAdmin = mock(AppUser.class);
            UserRoleAssignment roleAssignment = mock(UserRoleAssignment.class);

            when(regionAdmin.getStatus()).thenReturn(AppUserStatus.ACTIVE);
            when(roleAssignment.getRoleAssignmentId()).thenReturn(
                1L
            );
            when(roleAssignment.getAppUser()).thenReturn(regionAdmin);
            when(roleAssignment.getRole()).thenReturn(UserRole.REGION_ADMIN);
            return roleAssignment;
        }
    }
}
