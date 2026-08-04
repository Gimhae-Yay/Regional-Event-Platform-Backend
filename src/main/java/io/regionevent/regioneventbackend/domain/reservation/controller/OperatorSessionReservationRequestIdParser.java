package io.regionevent.regioneventbackend.domain.reservation.controller;

import java.util.regex.Pattern;

import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

final class OperatorSessionReservationRequestIdParser {

    private static final Pattern POSITIVE_DECIMAL_PATTERN = Pattern.compile("^[1-9][0-9]*$");

    private OperatorSessionReservationRequestIdParser() {
    }

    static Long parseRequired(String value) {
        if (!isPositiveDecimal(value)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, exception);
        }
    }

    static Long parseOrNull(String value) {
        if (!isPositiveDecimal(value)) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static boolean isPositiveDecimal(String value) {
        return value != null && POSITIVE_DECIMAL_PATTERN.matcher(value).matches();
    }
}
