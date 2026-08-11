package io.regionevent.regioneventbackend.domain.platformadmin.controller;

import java.util.regex.Pattern;

import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

final class PlatformAdminAccountIdParser {

    private static final Pattern POSITIVE_DECIMAL_PATTERN = Pattern.compile("^[1-9][0-9]*$");

    private PlatformAdminAccountIdParser() {
    }

    static Long toId(String value) {
        if (!POSITIVE_DECIMAL_PATTERN.matcher(value).matches()) {
            validateType(value);
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, exception);
        }
    }

    private static void validateType(String value) {
        try {
            Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.INVALID_TYPE, exception);
        }
    }
}
