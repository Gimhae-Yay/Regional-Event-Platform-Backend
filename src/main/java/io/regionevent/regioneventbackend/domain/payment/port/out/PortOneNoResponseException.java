package io.regionevent.regioneventbackend.domain.payment.port.out;

import io.regionevent.regioneventbackend.domain.payment.entity.RefundFailureReasonCode;

public class PortOneNoResponseException extends RuntimeException {

    private final RefundFailureReasonCode failureReasonCode;

    public PortOneNoResponseException(
        RefundFailureReasonCode failureReasonCode,
        Throwable cause
    ) {
        super(cause);
        this.failureReasonCode = failureReasonCode;
    }

    public RefundFailureReasonCode getFailureReasonCode() {
        return failureReasonCode;
    }
}
