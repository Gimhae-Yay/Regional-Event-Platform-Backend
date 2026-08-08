package io.regionevent.regioneventbackend.domain.stampbook.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookContent;
import io.regionevent.regioneventbackend.domain.stampbook.entity.StampbookContentId;

public interface StampbookContentRepository extends JpaRepository<StampbookContent, StampbookContentId> {
}
