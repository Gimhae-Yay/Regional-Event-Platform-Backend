package io.regionevent.regioneventbackend.domain.audit.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEvent;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    @Query("""
        SELECT new io.regionevent.regioneventbackend.domain.audit.repository.MissionHistoryAuditProjection(
            auditEvent.auditEventId,
            auditEvent.requestId,
            auditEvent.previousState,
            auditEvent.nextState,
            auditEvent.result,
            auditEvent.reasonCode,
            auditEvent.actorKind,
            actor.userId,
            auditEvent.occurredAt
        )
        FROM AuditEvent auditEvent
        LEFT JOIN AuditEventActorLink actorLink
            ON actorLink.auditEvent = auditEvent
        LEFT JOIN actorLink.actor actor
        WHERE auditEvent.targetType = io.regionevent.regioneventbackend.domain.audit.entity.AuditEventTargetType.MISSION
            AND auditEvent.targetId = :missionId
            AND auditEvent.result = io.regionevent.regioneventbackend.domain.audit.entity.AuditEventResult.SUCCESS
            AND auditEvent.occurredAt >= timestampadd(day, -90, CURRENT_TIMESTAMP)
        ORDER BY auditEvent.occurredAt ASC, auditEvent.auditEventId ASC
        """)
    List<MissionHistoryAuditProjection> findMissionHistoryAuditProjections(
        @Param("missionId") Long missionId
    );

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
                (
                    LOCATE(:qrCheckInPrefix, auditEvent.reasonCode) = 1
                    AND auditEvent.reasonCode <> :qrCheckInSuccessReasonCode
                )
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
        @Param("qrCheckInSuccessReasonCode") String qrCheckInSuccessReasonCode,
        @Param("reservationLookupReasonCode") String reservationLookupReasonCode,
        @Param("manualCheckInPrefix") String manualCheckInPrefix,
        Pageable pageable
    );

    @Query("""
        SELECT CASE WHEN COUNT(auditEvent) > 0 THEN true ELSE false END
        FROM AuditEvent auditEvent
        WHERE auditEvent.auditEventId = :cursorAuditEventId
            AND auditEvent.region.regionId = :regionId
            AND auditEvent.occurredAt = :cursorOccurredAt
            AND auditEvent.occurredAt >= :cutoff
            AND auditEvent.occurredAt <= :now
            AND (
                (
                    LOCATE(:qrCheckInPrefix, auditEvent.reasonCode) = 1
                    AND auditEvent.reasonCode <> :qrCheckInSuccessReasonCode
                )
                OR auditEvent.reasonCode = :reservationLookupReasonCode
                OR LOCATE(:manualCheckInPrefix, auditEvent.reasonCode) = 1
            )
        """)
    boolean existsQrExceptionCursorBoundary(
        @Param("regionId") Long regionId,
        @Param("cursorOccurredAt") Instant cursorOccurredAt,
        @Param("cursorAuditEventId") Long cursorAuditEventId,
        @Param("cutoff") Instant cutoff,
        @Param("now") Instant now,
        @Param("qrCheckInPrefix") String qrCheckInPrefix,
        @Param("qrCheckInSuccessReasonCode") String qrCheckInSuccessReasonCode,
        @Param("reservationLookupReasonCode") String reservationLookupReasonCode,
        @Param("manualCheckInPrefix") String manualCheckInPrefix
    );

    @Query("""
        SELECT new io.regionevent.regioneventbackend.domain.audit.repository.QrExceptionAuditProjection(
            auditEvent.auditEventId,
            auditEvent.region.regionId,
            auditEvent.targetType,
            auditEvent.targetId,
            auditEvent.result,
            auditEvent.reasonCode,
            auditEvent.occurredAt
        )
        FROM AuditEvent auditEvent
        WHERE auditEvent.auditEventId = :auditEventId
            AND (
                auditEvent.reasonCode = 'QR_VERIFICATION_FAILED'
                OR auditEvent.reasonCode LIKE 'MANUAL_CHECK_IN\\_%' ESCAPE '\\'
                OR (
                    auditEvent.reasonCode LIKE 'QR_CHECK_IN\\_%' ESCAPE '\\'
                    AND auditEvent.reasonCode <> 'QR_CHECK_IN_SUCCESS'
                )
            )
        """)
    Optional<QrExceptionAuditProjection> findQrExceptionAuditProjectionById(
        @Param("auditEventId") Long auditEventId
    );
}
