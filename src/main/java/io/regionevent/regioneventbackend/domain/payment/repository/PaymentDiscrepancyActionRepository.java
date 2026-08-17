package io.regionevent.regioneventbackend.domain.payment.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import io.regionevent.regioneventbackend.domain.payment.entity.PaymentDiscrepancyAction;

public interface PaymentDiscrepancyActionRepository extends JpaRepository<PaymentDiscrepancyAction, Long> {

    List<PaymentDiscrepancyAction> findAllByPaymentDiscrepancyPaymentDiscrepancyIdOrderByActedAtAscPaymentDiscrepancyActionIdAsc(
        Long paymentDiscrepancyId
    );
}
