package io.regionevent.regioneventbackend.domain.payment.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.regionevent.regioneventbackend.domain.payment.entity.RefundAttempt;
import io.regionevent.regioneventbackend.domain.payment.entity.RefundAttemptOutcomeKind;

public interface RefundAttemptRepository extends JpaRepository<RefundAttempt, Long> {

    List<RefundAttempt> findAllByRefundRefundIdIn(List<Long> refundIds);

    List<RefundAttempt> findAllByRefundRefundIdOrderByAttemptNoAsc(Long refundId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT attempt
        FROM RefundAttempt attempt
        WHERE attempt.refundAttemptId = :refundAttemptId
        """)
    Optional<RefundAttempt> findByRefundAttemptIdForUpdate(
        @Param("refundAttemptId") Long refundAttemptId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT attempt
        FROM RefundAttempt attempt
        WHERE attempt.outcomeKind = :outcomeKind
          AND attempt.attemptedAt <= :latestAttemptedAt
        ORDER BY attempt.attemptedAt ASC, attempt.refundAttemptId ASC
        """)
    List<RefundAttempt> findRecoveryCandidatesForUpdate(
        @Param("outcomeKind") RefundAttemptOutcomeKind outcomeKind,
        @Param("latestAttemptedAt") java.time.Instant latestAttemptedAt
    );
}
