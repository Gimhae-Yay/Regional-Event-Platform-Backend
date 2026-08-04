package io.regionevent.regioneventbackend.domain.audit.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEvent;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    @Query("""
        SELECT new io.regionevent.regioneventbackend.domain.audit.repository.QrExceptionReadProjection(
            auditEvent.auditEventId,
            auditEvent.occurredAt,
            auditEvent.region.regionId,
            auditEvent.targetType,
            auditEvent.targetId,
            auditEvent.result,
            auditEvent.reasonCode,
            reservation.reservationId,
            reservation.region.regionId,
            reservationSession.sessionId,
            reservationSession.region.regionId,
            reservationContent.contentId,
            reservationContent.region.regionId,
            visit.visitId,
            visit.region.regionId,
            visitReservation.reservationId,
            visitReservation.region.regionId,
            visitSession.sessionId,
            visitSession.region.regionId,
            visitContent.contentId,
            visitContent.region.regionId
        )
        FROM AuditEvent auditEvent
        LEFT JOIN Reservation reservation
            ON auditEvent.targetType = io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType.RESERVATION
            AND auditEvent.targetId = reservation.reservationId
        LEFT JOIN reservation.contentSession reservationSession
        LEFT JOIN reservationSession.content reservationContent
        LEFT JOIN Visit visit
            ON auditEvent.targetType = io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType.VISIT
            AND auditEvent.targetId = visit.visitId
        LEFT JOIN visit.reservation visitReservation
        LEFT JOIN visit.contentSession visitSession
        LEFT JOIN visit.content visitContent
        WHERE auditEvent.region.regionId = :regionId
            AND auditEvent.occurredAt >= :cutoff
            AND (
                LOCATE(:qrCheckInPrefix, auditEvent.reasonCode) = 1
                OR auditEvent.reasonCode = :reservationLookupReasonCode
                OR LOCATE(:manualCheckInPrefix, auditEvent.reasonCode) = 1
            )
            AND (
                :cursorOccurredAt IS NULL
                OR auditEvent.occurredAt < :cursorOccurredAt
                OR (
                    auditEvent.occurredAt = :cursorOccurredAt
                    AND auditEvent.auditEventId < :cursorAuditEventId
                )
            )
        ORDER BY auditEvent.occurredAt DESC, auditEvent.auditEventId DESC
        """)
    List<QrExceptionReadProjection> findQrExceptionReadProjections(
        @Param("regionId") Long regionId,
        @Param("cutoff") Instant cutoff,
        @Param("cursorOccurredAt") Instant cursorOccurredAt,
        @Param("cursorAuditEventId") Long cursorAuditEventId,
        @Param("qrCheckInPrefix") String qrCheckInPrefix,
        @Param("reservationLookupReasonCode") String reservationLookupReasonCode,
        @Param("manualCheckInPrefix") String manualCheckInPrefix,
        Pageable pageable
    );
}
