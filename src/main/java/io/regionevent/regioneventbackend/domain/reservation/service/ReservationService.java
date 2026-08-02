package io.regionevent.regioneventbackend.domain.reservation.service;

import java.time.Instant;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHold;
import io.regionevent.regioneventbackend.domain.reservation.entity.Reservation;
import io.regionevent.regioneventbackend.domain.reservation.repository.ReservationRepository;

@Service
public class ReservationService {

    private static final int IDENTIFIER_GENERATION_MAX_ATTEMPTS = 5;

    private final ReservationRepository reservationRepository;
    private final ReservationIdentifierGenerator reservationIdentifierGenerator;

    public ReservationService(
        ReservationRepository reservationRepository,
        ReservationIdentifierGenerator reservationIdentifierGenerator
    ) {
        this.reservationRepository = reservationRepository;
        this.reservationIdentifierGenerator = reservationIdentifierGenerator;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Reservation createConfirmed(CapacityHold capacityHold) {
        Instant confirmedAt = capacityHold.getTerminalAt();
        if (confirmedAt == null) {
            throw new IllegalArgumentException("consumed capacity hold must have terminalAt");
        }

        for (int attempt = 0; attempt < IDENTIFIER_GENERATION_MAX_ATTEMPTS; attempt++) {
            ReservationIdentifierGenerator.ReservationIdentifiers identifiers = reservationIdentifierGenerator
                .generate(confirmedAt);
            if (insertConfirmed(capacityHold, identifiers, confirmedAt)) {
                return reservationRepository.findByQrReference(identifiers.qrReference())
                    .orElseThrow(() -> new IllegalStateException("created reservation does not exist"));
            }
        }
        throw new IllegalStateException("failed to generate unique reservation identifiers");
    }

    @Transactional(readOnly = true)
    public Reservation findById(Long reservationId) {
        return reservationRepository.findByReservationIdForUpdate(reservationId)
            .orElseThrow(() -> new IllegalStateException("idempotency result reservation does not exist"));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public int cancelUncheckedInReservationsForSession(
        ContentSession contentSession,
        String cancellationReason,
        Instant cancelledAt
    ) {
        List<Reservation> confirmedReservations = reservationRepository.findConfirmedBySessionIdForUpdate(
            contentSession.getSessionId()
        );
        boolean shouldReleaseCapacity = cancelledAt.isBefore(contentSession.getStartsAt());
        Instant capacityReleasedAt = shouldReleaseCapacity ? cancelledAt : null;
        int releasedQuantity = shouldReleaseCapacity
            ? confirmedReservations.stream()
                .mapToInt(reservation -> reservation.getCapacityHold().getQuantity())
                .sum()
            : 0;
        confirmedReservations.forEach(reservation ->
            reservation.cancel(cancellationReason, cancelledAt, capacityReleasedAt)
        );
        reservationRepository.saveAllAndFlush(confirmedReservations);
        return releasedQuantity;
    }

    private boolean insertConfirmed(
        CapacityHold capacityHold,
        ReservationIdentifierGenerator.ReservationIdentifiers identifiers,
        Instant confirmedAt
    ) {
        try {
            return reservationRepository.insertConfirmed(
                identifiers.reservationNo(),
                identifiers.qrReference(),
                capacityHold.getRegion().getRegionId(),
                capacityHold.getHoldId(),
                capacityHold.getContentSession().getSessionId(),
                capacityHold.getUser().getUserId(),
                confirmedAt
            ) == 1;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }
}
