package io.regionevent.regioneventbackend.domain.payment.service;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.regionevent.regioneventbackend.domain.payment.entity.Payment;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentIdempotency;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentIdempotencyOperation;
import io.regionevent.regioneventbackend.domain.payment.entity.PaymentIdempotencyStatus;
import io.regionevent.regioneventbackend.domain.payment.repository.PaymentIdempotencyRepository;

@Service
public class PaymentIdempotencyService {

    private static final List<PaymentIdempotencyStatus> TERMINAL_STATUSES = List.of(
        PaymentIdempotencyStatus.SUCCEEDED,
        PaymentIdempotencyStatus.FAILED
    );

    private final PaymentIdempotencyRepository paymentIdempotencyRepository;

    public PaymentIdempotencyService(PaymentIdempotencyRepository paymentIdempotencyRepository) {
        this.paymentIdempotencyRepository = paymentIdempotencyRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public PaymentIdempotencyAcquireResult acquirePaymentCreate(
        long actorUserId,
        String keyHash,
        String requestHash
    ) {
        return paymentIdempotencyRepository.findByActorUserIdAndOperationAndIdempotencyKeyHashForUpdate(
            actorUserId,
            PaymentIdempotencyOperation.PAYMENT_CREATE,
            keyHash
        )
            .map(idempotency -> new PaymentIdempotencyAcquireResult(false, idempotency))
            .orElseGet(() -> new PaymentIdempotencyAcquireResult(
                true,
                paymentIdempotencyRepository.saveAndFlush(
                    new PaymentIdempotency(actorUserId, keyHash, requestHash)
                )
            ));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void setPaymentResultExpiration(Payment payment, Instant finalizedAt) {
        paymentIdempotencyRepository.findByPaymentPaymentIdForUpdate(payment.getPaymentId())
            .ifPresent(idempotency -> idempotency.setExpiresAtAfterPaymentFinalization(finalizedAt));
    }

    @Transactional
    public int deleteExpiredTerminalRecords() {
        return paymentIdempotencyRepository.deleteExpiredTerminalRecords(TERMINAL_STATUSES);
    }

    public record PaymentIdempotencyAcquireResult(
        boolean created,
        PaymentIdempotency idempotency
    ) {
    }
}
