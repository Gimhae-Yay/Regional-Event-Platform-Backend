package io.regionevent.regioneventbackend.domain.audit.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEvent;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {
}
