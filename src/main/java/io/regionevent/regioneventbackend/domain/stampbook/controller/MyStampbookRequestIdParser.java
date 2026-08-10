package io.regionevent.regioneventbackend.domain.stampbook.controller;

import java.util.regex.Pattern;

import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

final class MyStampbookRequestIdParser {

    private static final Pattern POSITIVE_DECIMAL_PATTERN = Pattern.compile("^[1-9][0-9]*$");

    private MyStampbookRequestIdParser() {
    }

    static Long parseRequired(String value) {
        Long stampbookId;
        try {
            stampbookId = Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.INVALID_TYPE, exception);
        }
        if (!POSITIVE_DECIMAL_PATTERN.matcher(value).matches()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return stampbookId;
    }
}
