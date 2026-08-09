package io.regionevent.regioneventbackend.domain.stampbook.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import io.regionevent.regioneventbackend.domain.stampbook.entity.Stampbook;

public interface StampbookRepository extends JpaRepository<Stampbook, Long> {
}
