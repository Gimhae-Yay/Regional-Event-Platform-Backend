package io.regionevent.regioneventbackend.domain.payment.controller;

import java.util.regex.Pattern;

import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

final class RefundFailureIdParser {

    private static final Pattern POSITIVE_DECIMAL_PATTERN = Pattern.compile("^[1-9][0-9]*$");

    private RefundFailureIdParser() {
    }

    static Long parseRequired(String value) {
        if (!POSITIVE_DECIMAL_PATTERN.matcher(value).matches()) {
            throw new BusinessException(ErrorCode.INVALID_TYPE);
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.INVALID_TYPE, exception);
        }
    }
}
