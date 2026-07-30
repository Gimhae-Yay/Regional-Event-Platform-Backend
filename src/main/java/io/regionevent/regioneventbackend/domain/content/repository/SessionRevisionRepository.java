package io.regionevent.regioneventbackend.domain.content.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import io.regionevent.regioneventbackend.domain.content.entity.SessionRevision;

public interface SessionRevisionRepository extends JpaRepository<SessionRevision, Long> {
}
