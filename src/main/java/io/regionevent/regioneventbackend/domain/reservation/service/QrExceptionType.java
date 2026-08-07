package io.regionevent.regioneventbackend.domain.reservation.service;

import java.util.Optional;

public enum QrExceptionType {
    QR_CHECK_IN_FAILURE,
    RESERVATION_NUMBER_LOOKUP,
    MANUAL_CHECK_IN;

    public static Optional<QrExceptionType> findByReasonCode(String reasonCode) {
        if (reasonCode == null || reasonCode.isBlank()) {
            return Optional.empty();
        }
        if ("QR_VERIFICATION_FAILED".equals(reasonCode)) {
            return Optional.of(RESERVATION_NUMBER_LOOKUP);
        }
        if (reasonCode.startsWith("MANUAL_CHECK_IN_")) {
            return Optional.of(MANUAL_CHECK_IN);
        }
        if (reasonCode.startsWith("QR_CHECK_IN_") && !"QR_CHECK_IN_SUCCESS".equals(reasonCode)) {
            return Optional.of(QR_CHECK_IN_FAILURE);
        }
        return Optional.empty();
    }
}
