package io.regionevent.regioneventbackend.domain.audit.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEvent;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

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
