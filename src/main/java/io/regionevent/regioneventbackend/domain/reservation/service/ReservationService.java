package io.regionevent.regioneventbackend.domain.reservation.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.content.entity.ContentSession;
import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHold;
import io.regionevent.regioneventbackend.domain.reservation.entity.Reservation;
import io.regionevent.regioneventbackend.domain.reservation.entity.ReservationStatus;
import io.regionevent.regioneventbackend.domain.reservation.repository.ReservationRepository;
import io.regionevent.regioneventbackend.domain.region.entity.Region;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;
import io.regionevent.regioneventbackend.global.security.qr.QrTokenService;

@Service
public class ReservationService {

    private static final int IDENTIFIER_GENERATION_MAX_ATTEMPTS = 5;

    private final ReservationRepository reservationRepository;
    private final ReservationIdentifierGenerator reservationIdentifierGenerator;
    private final QrTokenService qrTokenService;

    public ReservationService(
        ReservationRepository reservationRepository,
        ReservationIdentifierGenerator reservationIdentifierGenerator,
        QrTokenService qrTokenService
    ) {
        this.reservationRepository = reservationRepository;
        this.reservationIdentifierGenerator = reservationIdentifierGenerator;
        this.qrTokenService = qrTokenService;
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

    @Transactional(readOnly = true)
    public MyReservationQrResult issueQr(Long reservationId, AppUser user) {
        Reservation reservation = reservationRepository.findByReservationIdForQrIssue(reservationId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (reservation.getUser() == null) {
            throw new BusinessException(ErrorCode.QR_ISSUE_CONFLICT);
        }
        if (!reservation.getUser().getUserId().equals(user.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        ContentSession contentSession = reservation.getContentSession();
        Instant issuedAt = toInstant(reservationRepository.findCurrentEpochSeconds());
        if (reservation.getStatus() != ReservationStatus.CONFIRMED
            || contentSession.getStatus() != ContentSessionStatus.SCHEDULED
            || issuedAt.isBefore(contentSession.getCheckinOpenAt())
            || !issuedAt.isBefore(contentSession.getCheckinCloseAt())) {
            throw new BusinessException(ErrorCode.QR_ISSUE_CONFLICT);
        }

        QrTokenService.IssuedQrToken issuedToken = qrTokenService.issue(
            reservation.getQrReference(),
            contentSession.getSessionId(),
            issuedAt,
            contentSession.getCheckinCloseAt()
        );
        return new MyReservationQrResult(
            reservation.getReservationId(),
            contentSession.getSessionId(),
            issuedToken.token(),
            issuedAt,
            issuedToken.expiresAt(),
            contentSession.getCheckinCloseAt()
        );
    }

    private Instant toInstant(BigDecimal epochSeconds) {
        long seconds = epochSeconds.longValue();
        return Instant.ofEpochSecond(seconds, epochSeconds.remainder(BigDecimal.ONE).movePointRight(9).longValue());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public List<NoShowReservationAuditTarget> expireNoShowReservations(Long sessionId) {
        return reservationRepository.findConfirmedReservationIdsBySessionId(
            sessionId,
            ReservationStatus.CONFIRMED
        ).stream()
            .map(this::expireIfNoShowEligible)
            .flatMap(Optional::stream)
            .toList();
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

    private Optional<NoShowReservationAuditTarget> expireIfNoShowEligible(Long reservationId) {
        if (reservationRepository.expireIfNoShowEligible(reservationId) == 0) {
            return Optional.empty();
        }
        Reservation reservation = reservationRepository.findByReservationIdAndStatus(
            reservationId,
            ReservationStatus.EXPIRED
        ).orElseThrow(() -> new IllegalStateException("expired reservation does not exist"));
        return Optional.of(NoShowReservationAuditTarget.from(reservation));
    }

    public record NoShowReservationAuditTarget(
        Long reservationId,
        Region region,
        Instant expiredAt
    ) {

        private static NoShowReservationAuditTarget from(Reservation reservation) {
            return new NoShowReservationAuditTarget(
                reservation.getReservationId(),
                reservation.getRegion(),
                reservation.getExpiredAt()
            );
        }
    }
}
