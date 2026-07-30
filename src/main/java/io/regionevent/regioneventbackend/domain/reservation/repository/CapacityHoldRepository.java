package io.regionevent.regioneventbackend.domain.reservation.repository;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.regionevent.regioneventbackend.domain.content.entity.ContentSessionStatus;
import io.regionevent.regioneventbackend.domain.content.entity.ContentStatus;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHold;
import io.regionevent.regioneventbackend.domain.reservation.entity.CapacityHoldStatus;

public interface CapacityHoldRepository extends JpaRepository<CapacityHold, Long> {

    @Modifying(flushAutomatically = true)
    @Query("""
        UPDATE CapacityHold capacityHold
        SET capacityHold.status = :consumedStatus,
            capacityHold.terminalAt = :confirmedAt
        WHERE capacityHold.holdId = :holdId
          AND capacityHold.user.userId = :userId
          AND capacityHold.status = :activeStatus
          AND capacityHold.expiresAt > :confirmedAt
          AND capacityHold.contentSession.content.status = :publishedStatus
          AND capacityHold.contentSession.status = :scheduledStatus
          AND capacityHold.contentSession.startsAt > :confirmedAt
        """)
    int consumeIfConfirmable(
        @Param("holdId") Long holdId,
        @Param("userId") Long userId,
        @Param("activeStatus") CapacityHoldStatus activeStatus,
        @Param("consumedStatus") CapacityHoldStatus consumedStatus,
        @Param("publishedStatus") ContentStatus publishedStatus,
        @Param("scheduledStatus") ContentSessionStatus scheduledStatus,
        @Param("confirmedAt") Instant confirmedAt
    );

    @Query(value = "SELECT CURRENT_TIMESTAMP(6)", nativeQuery = true)
    Instant findCurrentTimestamp();
}
