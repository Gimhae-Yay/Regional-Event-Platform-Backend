package io.regionevent.regioneventbackend.domain.reservation.controller;

import java.util.regex.Pattern;

import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

final class MyReservationDetailRequestIdParser {

    private static final Pattern POSITIVE_DECIMAL_PATTERN = Pattern.compile("^[1-9][0-9]*$");

    private MyReservationDetailRequestIdParser() {
    }

    static Long parseRequired(String value) {
        Long reservationId;
        try {
            reservationId = Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.INVALID_TYPE, exception);
        }
        if (!POSITIVE_DECIMAL_PATTERN.matcher(value).matches()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return reservationId;
    }

    static Long parseOrNull(String value) {
        try {
            Long reservationId = Long.valueOf(value);
            return POSITIVE_DECIMAL_PATTERN.matcher(value).matches() ? reservationId : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
