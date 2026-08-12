package io.regionevent.regioneventbackend.domain.payment.service;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.payment.entity.Payment;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentStatus;
import io.regionevent.regioneventbackend.domain.payment.repository.PaymentRepository;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;

    public PaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Payment> findPendingByHoldIdForUpdate(Long holdId) {
        return paymentRepository.findByHoldIdAndStatusForUpdate(holdId, PaymentStatus.PENDING);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Payment> findByOrderIdForUpdate(String orderId) {
        return paymentRepository.findByOrderIdForUpdate(orderId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Payment> findByPaymentIdForUpdate(Long paymentId) {
        return paymentRepository.findByPaymentIdForUpdate(paymentId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Payment> findWebhookTargetByOrderIdForUpdate(String orderId) {
        return paymentRepository.findWebhookTargetByOrderIdForUpdate(orderId);
    }

    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public Optional<Payment> findByOrderId(String orderId) {
        return paymentRepository.findByOrderId(orderId);
    }

    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public Optional<Payment> findByReservationId(Long reservationId) {
        return paymentRepository.findByReservationReservationId(reservationId);
    }

    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public boolean hasPendingPayment(Long userId) {
        return paymentRepository.existsByCapacityHoldUserUserIdAndStatus(userId, PaymentStatus.PENDING);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Payment create(Payment payment) {
        return paymentRepository.saveAndFlush(payment);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Payment> expirePendingByHoldId(Long holdId, Instant expiredAt) {
        return findPendingByHoldIdForUpdate(holdId)
            .map(payment -> {
                payment.expire(expiredAt);
                return payment;
            });
    }
}
