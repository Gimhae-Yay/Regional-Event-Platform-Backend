package io.regionevent.regioneventbackend.domain.reservation.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult;
import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventActor;
import io.regionevent.regioneventbackend.domain.audit.service.AuditEventCommand;
import io.regionevent.regioneventbackend.domain.audit.service.RecordAuditEventUseCase;
import io.regionevent.regioneventbackend.domain.reservation.entity.Reservation;
import io.regionevent.regioneventbackend.domain.user.entity.UserRoleAssignment;
import io.regionevent.regioneventbackend.domain.user.service.RegionAdminAuthorizationService;

@Service
public class SearchRegionAdminReservationByNumberUseCase {

    private static final String QR_VERIFICATION_FAILED = "QR_VERIFICATION_FAILED";

    private final ReservationReadService reservationReadService;
    private final ReservationService reservationService;
    private final RegionAdminAuthorizationService regionAdminAuthorizationService;
    private final ReservationParticipantMasker reservationParticipantMasker;
    private final RecordAuditEventUseCase recordAuditEventUseCase;

    public SearchRegionAdminReservationByNumberUseCase(
        ReservationReadService reservationReadService,
        ReservationService reservationService,
        RegionAdminAuthorizationService regionAdminAuthorizationService,
        ReservationParticipantMasker reservationParticipantMasker,
        RecordAuditEventUseCase recordAuditEventUseCase
    ) {
        this.reservationReadService = reservationReadService;
        this.reservationService = reservationService;
        this.regionAdminAuthorizationService = regionAdminAuthorizationService;
        this.reservationParticipantMasker = reservationParticipantMasker;
        this.recordAuditEventUseCase = recordAuditEventUseCase;
    }

    @Transactional
    public RegionAdminReservationSearchResult search(
        Long userId,
        String reservationNo,
        UUID requestId
    ) {
        ReservationReadResult readResult = reservationReadService.findByReservationNo(reservationNo);
        Reservation reservation = reservationService.findByReservationNoForAuthorizedLookup(reservationNo);
        UserRoleAssignment roleAssignment = regionAdminAuthorizationService.authorize(
            userId,
            reservation.getRegion().getRegionId()
        );
        Instant databaseNow = reservationService.findCurrentDatabaseInstant();

        recordAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            reservation.getRegion(),
            AuditEventTargetType.RESERVATION,
            reservation.getReservationId(),
            reservation.getStatus().name(),
            reservation.getStatus().name(),
            AuditEventResult.SUCCESS,
            QR_VERIFICATION_FAILED,
            new AuditEventActor(roleAssignment),
            databaseNow
        ));

        ReservationReadSnapshot snapshot = readResult.snapshot();
        return new RegionAdminReservationSearchResult(
            snapshot.reservation(),
            snapshot.session(),
            snapshot.content(),
            reservationParticipantMasker.mask(snapshot.participant()),
            readResult.checkIn()
        );
    }
}
