package io.regionevent.regioneventbackend.domain.visit.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
    @Lock(LockModeType.PESSIMISTIC_READ)
    Optional<Visit> findByVisitId(Long visitId);

    @EntityGraph(attributePaths = {
        "region",
        "content",
        "user"
    })
    @Query("""
        SELECT visit
        FROM Visit visit
        WHERE visit.visitId = :visitId
        """)
    Optional<Visit> findMissionProgressSourceByVisitId(@Param("visitId") Long visitId);

    @EntityGraph(attributePaths = {
        "region",
        "content",
        "user"
    })
    @Query("""
        SELECT visit
        FROM Visit visit
        WHERE visit.visitId = :visitId
        """)
    Optional<Visit> findStampbookProgressSourceByVisitId(@Param("visitId") Long visitId);

    @EntityGraph(attributePaths = {
        "region",
        "reservation",
        "reservation.contentSession",
        "content",
        "contentSession",
        "checkedInByUser",
        "user"
    })
    @Lock(LockModeType.PESSIMISTIC_READ)
    Optional<Visit> findByReservationReservationId(Long reservationId);

    @Query("""
        SELECT visit.reservation.reservationId
        FROM Visit visit
        WHERE visit.visitId = :visitId
        """)
    Optional<Long> findReservationIdByVisitId(@Param("visitId") Long visitId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
        UPDATE visit
        SET user_id = NULL,
            author_unlinked_at = CURRENT_TIMESTAMP
        WHERE user_id = :userId
        """, nativeQuery = true)
    int unlinkAuthorByUserId(@Param("userId") Long userId);
}
