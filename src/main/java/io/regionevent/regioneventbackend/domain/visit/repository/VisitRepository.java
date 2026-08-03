package io.regionevent.regioneventbackend.domain.visit.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import io.regionevent.regioneventbackend.domain.visit.entity.Visit;

public interface VisitRepository extends JpaRepository<Visit, Long> {

    @EntityGraph(attributePaths = {
        "region",
        "reservation",
        "reservation.contentSession",
        "content",
        "contentSession",
        "checkedInByUser",
        "user"
    })
    Optional<Visit> findByVisitId(Long visitId);

    @EntityGraph(attributePaths = {
        "region",
        "reservation",
        "reservation.contentSession",
        "content",
        "contentSession",
        "checkedInByUser",
        "user"
    })
    Optional<Visit> findByReservationReservationId(Long reservationId);
}
