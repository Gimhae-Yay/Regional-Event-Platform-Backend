package io.regionevent.regioneventbackend.domain.payment.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.regionevent.regioneventbackend.domain.payment.entity.PaymentDiscrepancy;

public interface PaymentDiscrepancyRepository extends JpaRepository<PaymentDiscrepancy, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT discrepancy
        FROM PaymentDiscrepancy discrepancy
        WHERE discrepancy.payment.paymentId = :paymentId
        """)
    Optional<PaymentDiscrepancy> findByPaymentIdForUpdate(@Param("paymentId") Long paymentId);
}
