package io.regionevent.regioneventbackend.domain.payment.service;

import java.util.Objects;

import org.springframework.stereotype.Service;

import io.regionevent.regioneventbackend.domain.payment.entity.Payment;
import io.regionevent.regioneventbackend.domain.payment.repository.PaymentRepository;
import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

@Service
public class PaymentReadService {

    private final PaymentRepository paymentRepository;

    public PaymentReadService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    public Payment findOwnedByPaymentId(Long userId, Long paymentId) {
        validateId(userId);
        validateId(paymentId);

        Payment payment = paymentRepository.findByPaymentId(paymentId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!Objects.equals(payment.getCapacityHold().getUser().getUserId(), userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return payment;
    }

    private void validateId(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }
}
