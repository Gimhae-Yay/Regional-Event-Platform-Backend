package io.regionevent.regioneventbackend.domain.content.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import io.regionevent.regioneventbackend.domain.content.entity.ContentRevision;

public interface ContentRevisionRepository extends JpaRepository<ContentRevision, Long> {
}
