package io.regionevent.regioneventbackend.domain.content.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import io.regionevent.regioneventbackend.domain.content.entity.ContentLog;

public interface ContentLogRepository extends JpaRepository<ContentLog, Long> {
}
