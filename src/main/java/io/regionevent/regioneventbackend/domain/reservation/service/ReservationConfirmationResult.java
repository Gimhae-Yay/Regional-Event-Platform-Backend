package io.regionevent.regioneventbackend.domain.reservation.service;

import io.regionevent.regioneventbackend.domain.reservation.dto.ConfirmReservationResponse;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

public record ReservationConfirmationResult(
    ConfirmReservationResponse response,
    ErrorCode errorCode
) {

    public static ReservationConfirmationResult success(ConfirmReservationResponse response) {
        return new ReservationConfirmationResult(response, null);
    }

    public static ReservationConfirmationResult failure(ErrorCode errorCode) {
        return new ReservationConfirmationResult(null, errorCode);
    }

    public boolean isSuccessful() {
        return response != null;
    }
}
