package io.regionevent.regioneventbackend.domain.audit.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import io.regionevent.regioneventbackend.domain.audit.entity.AuditEventActorLink;

public interface AuditEventActorLinkRepository extends JpaRepository<AuditEventActorLink, Long> {
}
