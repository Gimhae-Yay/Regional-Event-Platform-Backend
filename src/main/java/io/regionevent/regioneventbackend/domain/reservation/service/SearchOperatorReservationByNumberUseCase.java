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
import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;
import io.regionevent.regioneventbackend.domain.reservation.entity.Reservation;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationStatus;
import io.regionevent.regioneventbackend.domain.user.service.OperatorAuthorizationService;

@Service
public class SearchOperatorReservationByNumberUseCase {

    private static final String QR_VERIFICATION_FAILED = "QR_VERIFICATION_FAILED";

    private final ReservationReadService reservationReadService;
    private final ReservationService reservationService;
    private final OperatorAuthorizationService operatorAuthorizationService;
    private final ReservationParticipantMasker reservationParticipantMasker;
    private final RecordAuditEventUseCase recordAuditEventUseCase;

    public SearchOperatorReservationByNumberUseCase(
        ReservationReadService reservationReadService,
        ReservationService reservationService,
        OperatorAuthorizationService operatorAuthorizationService,
        ReservationParticipantMasker reservationParticipantMasker,
        RecordAuditEventUseCase recordAuditEventUseCase
    ) {
        this.reservationReadService = reservationReadService;
        this.reservationService = reservationService;
        this.operatorAuthorizationService = operatorAuthorizationService;
        this.reservationParticipantMasker = reservationParticipantMasker;
        this.recordAuditEventUseCase = recordAuditEventUseCase;
    }

    @Transactional
    public OperatorReservationSearchResult search(
        Long userId,
        String reservationNo,
        UUID requestId
    ) {
        ReservationReadResult readResult = reservationReadService.findByReservationNo(reservationNo);
        Reservation reservation = reservationService.findByReservationNoForOperatorLookup(reservationNo);
        OperatorAuthorizationService.AuthorizedOperator authorizedOperator = operatorAuthorizationService
            .authorizeOwnedContent(
                userId,
                reservation.getContentSession().getContent().getOperator(),
                reservation.getContentSession().getContent().getRegion()
            );
        Instant databaseNow = reservationService.findCurrentDatabaseInstant();
        boolean canCheckIn = canCheckIn(readResult.snapshot(), databaseNow);

        recordAuditEventUseCase.record(new AuditEventCommand(
            requestId,
            reservation.getRegion(),
            AuditEventTargetType.RESERVATION,
            reservation.getReservationId(),
            reservation.getStatus().name(),
            reservation.getStatus().name(),
            AuditEventResult.SUCCESS,
            QR_VERIFICATION_FAILED,
            new AuditEventActor(authorizedOperator.roleAssignment()),
            databaseNow
        ));

        ReservationReadSnapshot snapshot = readResult.snapshot();
        return new OperatorReservationSearchResult(
            snapshot.reservation(),
            snapshot.session(),
            snapshot.content(),
            reservationParticipantMasker.mask(snapshot.participant()),
            readResult.checkIn(),
            canCheckIn
        );
    }

    private boolean canCheckIn(
        ReservationReadSnapshot snapshot,
        Instant databaseNow
    ) {
        return snapshot.reservation().status() == ReservationStatus.CONFIRMED
            && snapshot.session().status() == ContentSessionStatus.SCHEDULED
            && !databaseNow.isBefore(snapshot.session().checkinOpenAt())
            && databaseNow.isBefore(snapshot.session().checkinCloseAt());
    }
}
