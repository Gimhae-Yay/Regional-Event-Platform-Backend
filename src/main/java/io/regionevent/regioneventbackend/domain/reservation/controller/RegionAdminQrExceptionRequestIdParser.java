package io.regionevent.regioneventbackend.domain.reservation.controller;

import io.regionevent.regioneventbackend.global.error.BusinessException;
import io.regionevent.regioneventbackend.global.error.ErrorCode;

final class RegionAdminQrExceptionRequestIdParser {

    private RegionAdminQrExceptionRequestIdParser() {
    }

    static Long parseRequired(String exceptionId) {
        if (exceptionId == null || exceptionId.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_TYPE);
        }
        if (exceptionId.startsWith("-")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        if (!exceptionId.matches("^[0-9]+$")) {
            throw new BusinessException(ErrorCode.INVALID_TYPE);
        }
        if (!exceptionId.matches("^[1-9][0-9]*$")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        try {
            return Long.valueOf(exceptionId);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.INVALID_TYPE, exception);
        }
    }

    static Long parseOrNull(String exceptionId) {
        try {
            return parseRequired(exceptionId);
        } catch (BusinessException exception) {
            return null;
        }
    }
}
