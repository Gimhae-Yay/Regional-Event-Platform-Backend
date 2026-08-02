package io.regionevent.regioneventbackend.domain.operator.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import io.regionevent.regioneventbackend.domain.operator.entity.OperatorApplication;
import io.regionevent.regioneventbackend.domain.operator.entity.OperatorApplicationStatus;
import io.regionevent.regioneventbackend.domain.user.entity.AppUser;

public interface OperatorApplicationRepository extends JpaRepository<OperatorApplication, Long> {

    boolean existsByApplicantAndStatus(AppUser applicant, OperatorApplicationStatus status);
}
