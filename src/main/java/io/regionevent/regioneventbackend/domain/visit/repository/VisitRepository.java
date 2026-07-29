package io.regionevent.regioneventbackend.domain.visit.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import io.regionevent.regioneventbackend.domain.visit.entity.Visit;

public interface VisitRepository extends JpaRepository<Visit, Long> {
}
