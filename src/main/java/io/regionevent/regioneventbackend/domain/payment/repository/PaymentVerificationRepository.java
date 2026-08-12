package io.regionevent.regioneventbackend.domain.payment.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import io.regionevent.regioneventbackend.domain.payment.entity.PaymentVerification;

public interface PaymentVerificationRepository extends JpaRepository<PaymentVerification, Long> {

    List<PaymentVerification> findAllByPaymentPaymentIdOrderByVerifiedAtAscPaymentVerificationIdAsc(
        Long paymentId
    );
}
