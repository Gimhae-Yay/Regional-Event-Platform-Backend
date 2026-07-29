package io.regionevent.regioneventbackend.domain.operator.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import io.regionevent.regioneventbackend.domain.operator.entity.OperatorApplication;

public interface OperatorApplicationRepository extends JpaRepository<OperatorApplication, Long> {
}
